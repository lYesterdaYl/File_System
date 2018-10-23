import java.io.*;
import java.util.Scanner;

public class File_System{
    public static final int DATA_BLK_START = 7;    // = K
    public static final int NUM_DESCRIPTORS = 24;  // start from blk 1 to 6
                                                        // each descriptor=4 ints=16 bytes
                                                        // each block=4 descriptors
    public static final int NUM_DESC_PER_BLK = 4;
    public static final int DESC_SIZE = 16;        // 16 bytes

    public static final int OFT_SIZE = 4;
    public static final int FILE_NAME_LEN = 4;

    private IO_System io;
    private OFTEntry[] oft;

    class OFTEntry{
        byte[] buffer = new byte[IO_System.B];
        int pos = 0;
        int index = 0;  // descriptor index
    }
    
    // default constructor
    public File_System(){
        io = new IO_System();
        oft = new OFTEntry[OFT_SIZE];
        init();
    }

    // init the file system
    public void init(){
        io.clear();
        byte[] buf = new byte[IO_System.B];

        // first K blocks are used
        for (int i = 0; i < DATA_BLK_START; i++){
            IO_System.setBit(buf, true, i);
        }

        io.writeBlock(0, buf);

        // clear oft entries
        for (int i = 0; i < OFT_SIZE; i++){
            oft[i] = null;
        }

        // init directory descritpor, each descriptor = 4 ints
        io.readBlock(1, buf);
        // length=0, 3blk=-1, -1, -1, blk=-1 mean unused
        IO_System.packArr(buf, new int[]{0, -1, -1, -1}, 0);
        io.writeBlock(1, buf);

        openDir();
    }

    // open the directory
    private void openDir(){
        if (oft[0] != null){
            return;
        }

        // open directory as first file
        oft[0] = new OFTEntry();

        // read first file
        int[] desc = readDesc(0);
        if (desc[0] > 0){
            io.readBlock(desc[1], oft[0].buffer);
        }
    }

    // load file descripty by index
    public int[] readDesc(int idx){
        byte[] buf = new byte[IO_System.B];
        int blk = idx / NUM_DESC_PER_BLK + 1;
        io.readBlock(blk, buf);

        int off = idx % NUM_DESC_PER_BLK;
        return IO_System.unpackArr(buf, off*DESC_SIZE, 4);
    }

    public void writeDesc(int idx, int[] desc){
        int blk = idx / NUM_DESC_PER_BLK + 1;
        byte[] buf = new byte[IO_System.B];
        io.readBlock(blk, buf);

        int off = idx % NUM_DESC_PER_BLK;
        IO_System.packArr(buf, desc, off*DESC_SIZE);
        io.writeBlock(blk, buf);
    }

    // load disk from file, return true if file exists
    public boolean loadDisk(String fname){
        try{
            io.loadFile(fname);

            // clear oft entries
            for (int i = 0; i < OFT_SIZE; i++){
                oft[i] = null;
            }

            openDir();

            return true;

        } catch (Exception e){
            init();
            return false;
        }
    }

    // save the file
    public boolean saveDisk(String fname){
        for (int i = 0; i < oft.length; i++){
            if (oft[i] != null && !close(i))
                return false;
        }

        try{
            io.saveFile(fname);

            openDir();

            return true;
        } catch (IOException e){
            return false;
        }
    }

    // open the named file, return the index , -1=fail
    public int open(String fname){
        fname = fname.trim();
        if (fname.length() > FILE_NAME_LEN || fname.isEmpty()){
            return -1;
        }

        if (!lseek(0, 0)){
            return -1;
        }
        byte[] entry = new byte[8];

        while (read(0, entry, 8) == 8){
            String oldfname = IO_System.unpackStr(entry, 0).trim();
            int descIdx = IO_System.unpack(entry, 4);

            if (oldfname.equals(fname)){
                // find the file
                // check if opened
                int emptyIdx = -1;
                for (int i = 0; i < oft.length; i++)
                {
                    if (oft[i] != null && oft[i].index == descIdx)
                        return i;
                    if (oft[i] == null && emptyIdx < 0)
                        emptyIdx = i;
                }
                // no empty oft entry
                if (emptyIdx < 0)
                    return -1;

                // open directory as first file
                oft[emptyIdx] = new OFTEntry();
                oft[emptyIdx].index = descIdx;

                // read first file
                int[] desc = readDesc(descIdx);
                if (desc[0] > 0){
                    io.readBlock(desc[1], oft[emptyIdx].buffer);
                }
                return emptyIdx;
            }
        }

        return -1;
    }


    // close the file
    public boolean close(int index){
        if (index < 0 || index > oft.length){
            return false;
        }
        if (oft[index] == null){
            return false;
        }

        // get the file descriptor
        int[] desc = readDesc(oft[index].index);
        // desc[0]=filelength, desc[1]=blk0, desc[2]=blk1, desc[3]=blk2

        // write blk data back
        int blkIdx = oft[index].pos / IO_System.B;
        if (blkIdx < desc.length-1 && desc[blkIdx+1] > 0){
            int blk = desc[blkIdx+1]; // -1=unused, 0=freenode, >0=used
            io.writeBlock(blk, oft[index].buffer);  // write back
        }

        // update file length
        if (oft[index].pos > desc[0]){
            desc[0] = oft[index].pos;

            // update descriptor
            writeDesc(oft[index].index, desc);
        }

        // Free the OFT entry
        oft[index] = null;

        return true;
    }

    // seek to position
    public boolean lseek(int index, int pos){
        if (index < 0 || index > oft.length){
            return false;
        }
        if (oft[index] == null){
            return false;
        }

        int oldblkIdx = oft[index].pos / IO_System.B;
        int newblkIdx = pos / IO_System.B;

        // get the file descriptor
        int[] desc = readDesc(oft[index].index);
        if (pos > desc[0])  // seek pos > file length
            return false;

        if (newblkIdx != oldblkIdx){
            // write back old data
            if (oldblkIdx < desc.length-1 && desc[oldblkIdx+1] > 0){
                int blk = desc[oldblkIdx+1]; // -1=unused, 0=freenode, >0=used
                io.writeBlock(blk, oft[index].buffer);  // write back
            }

            // read new data to buffer
            if (newblkIdx < desc.length-1 && desc[newblkIdx+1] > 0){
                int blk = desc[newblkIdx+1];
                io.readBlock(blk, oft[index].buffer);
            }
        }

        oft[index].pos = pos;
        return true;
    }

    // read data from the file, return number of bytes read, -1=failed
    public int read(int index, byte[] data, int count){
        if (index < 0 || index > oft.length){
            return -1;
        }
        if (oft[index] == null){
            return -1;
        }

        // get the file descriptor
        int[] desc = readDesc(oft[index].index);
        if (oft[index].pos + count > desc[0]){
            count = desc[0] - oft[index].pos;//return false;
        }
        if (count < 0){
            return -1;
        }

        int startPos = oft[index].pos % IO_System.B;
        int readed = 0;
        while (count > 0){
            // calculate real count
            int rcnt = count;
            if (startPos + rcnt > IO_System.B){
                rcnt = IO_System.B - startPos;
            }

            // read bytes
            for (int i = 0; i < rcnt; i++){
                data[readed+i] = oft[index].buffer[startPos + i];
            }

            readed += rcnt;
            oft[index].pos += rcnt;
            count -= rcnt;

            if (startPos + rcnt == IO_System.B){
                // write old data
                int oldblkIdx = (oft[index].pos-1) / IO_System.B;
                if (oldblkIdx < desc.length-1 && desc[oldblkIdx+1] > 0){
                    int blk = desc[oldblkIdx+1]; // -1=unused, 0=freenode, >0=used
                    io.writeBlock(blk, oft[index].buffer);  // write back
                }

                // read new data to buffer
                int newblkIdx = oldblkIdx+1;
                if (newblkIdx < desc.length-1 && desc[newblkIdx+1] > 0){
                    int blk = desc[newblkIdx+1];
                    io.readBlock(blk, oft[index].buffer);
                }
                startPos = 0;
            }
        }

        return readed;
    }

    // write data to the file
    public boolean write(int index, byte[] data, int count){
        if (index < 0 || index > oft.length){
            return false;
        }
        if (oft[index] == null){
            return false;
        }

        // maximum to 3 blocks
        if (oft[index].pos + count > IO_System.B * 3){
            return false;
        }

        // get the file descriptor
        int[] desc = readDesc(oft[index].index);
        if (oft[index].pos + count > desc[0]){
            // increase file size and block
            int nflen = oft[index].pos + count;
            int oldNumBlks = desc[0] / IO_System.B + (desc[0] % IO_System.B > 0 ? 1 : 0);
            int newNumBlks = nflen / IO_System.B + (nflen % IO_System.B > 0 ? 1 : 0);

            // allocate new blocks
            if (newNumBlks > oldNumBlks){
                int blks = newNumBlks - oldNumBlks;
                int[] blkIdx = new int[blks];

                // find empty block
                byte[] tmp = new byte[IO_System.B];
                io.readBlock(0, tmp);
                int found = 0;

                for (int i = DATA_BLK_START; i < IO_System.L && found < blks; i++){
                    if (!IO_System.getBit(tmp, i)){
                        blkIdx[found] = i;
                        found++;
                    }
                }
                // not enough blocks
                if (found != blks){
                    return false;
                }

                // allocate new blk
                for (int i = 0; i < blkIdx.length; i++){
                    IO_System.setBit(tmp, true, blkIdx[i]);
                    desc[oldNumBlks+i+1] = blkIdx[i];
                }

                // update bitmap
                io.writeBlock(0, tmp);

            }
            desc[0] = oft[index].pos + count;
            writeDesc(oft[index].index, desc);
        }

        int startPos = oft[index].pos % IO_System.B;
        int written = 0;
        while (count > 0){
            // calculate real count
            int rcnt = count;
            if (startPos + rcnt > IO_System.B){
                rcnt = IO_System.B - startPos;
            }

            // write to buffer
            for (int i = 0; i < rcnt; i++){
                oft[index].buffer[startPos + i] = data[written+i];
            }

            written += rcnt;
            oft[index].pos += rcnt;
            count -= rcnt;

            if (startPos + rcnt == IO_System.B){
                // write old data
                int oldblkIdx = (oft[index].pos-1) / IO_System.B;
                if (oldblkIdx < desc.length-1 && desc[oldblkIdx+1] > 0){
                    int blk = desc[oldblkIdx+1]; // -1=unused, 0=freenode, >0=used
                    io.writeBlock(blk, oft[index].buffer);  // write back
                }

                // read new data to buffer
                int newblkIdx = oldblkIdx+1;
                if (newblkIdx < desc.length-1 && desc[newblkIdx+1] > 0){
                    int blk = desc[newblkIdx+1];
                    io.readBlock(blk, oft[index].buffer);
                }
                startPos = 0;
            }
        }

        return true;
    }

    // list the directory
    public String directory(){
        if (!lseek(0, 0)){
            return null;
        }
        byte[] entry = new byte[8];
        String list = "";

        while (read(0, entry, 8) == 8){
            String fname = IO_System.unpackStr(entry, 0).trim();

            if (!fname.isEmpty()){
                if (!list.isEmpty()){
                    list += " ";
                }
                list += fname;
            }
        }

        return list;
    }

    // create the file
    public boolean create(String fname){
        fname = fname.trim();
        if (fname.length() > FILE_NAME_LEN || fname.isEmpty()){
            return false;
        }
        // search for a free descriptor
        int freeDesc = -1;
        byte[] buf = new byte[IO_System.B];
        for (int i = 1; i < DATA_BLK_START && freeDesc < 0; i++){
            io.readBlock(i, buf);
            for (int p = 0; p < NUM_DESC_PER_BLK && freeDesc < 0; p++){
                int[] desc = IO_System.unpackArr(buf, p*DESC_SIZE, 4);
                // blkno=0 mean the descriptor is empty
                if (desc[1] == 0 && desc[2] == 0 && desc[3] == 0){
                    freeDesc = (i-1) * NUM_DESC_PER_BLK + p;
                }
            }
        }
        // no empty descriptor
        if (freeDesc < 0){
            return false;
        }

        // search directory entry
        if (!lseek(0, 0)){
            return false;
        }
        byte[] entry = new byte[8];

        int emptyPos = -1;
        while (read(0, entry, 8) == 8){
            String oldfname = IO_System.unpackStr(entry, 0).trim();

            if (oldfname.equals(fname)){
                return false;
            }

            if (fname.isEmpty()){
                emptyPos = oft[0].pos - 8;
                break;
            }
        }

        // write entry
        IO_System.packStr(entry, fname, 0);
        IO_System.pack(entry, freeDesc, 4);

        if (emptyPos >= 0){
            if (!lseek(0, emptyPos)){
                return false;
            }
        }
        if (!write(0, entry, 8)){
            return false;
        }

        // update the descriptor
        int[] desc = new int[] {0, -1, -1, -1};
        writeDesc(freeDesc, desc);

        return true;
    }

    // destroy the named file.
    public boolean destroy(String fname){
        fname = fname.trim();
        if (fname.length() > FILE_NAME_LEN || fname.isEmpty()){
            return false;
        }

        if (!lseek(0, 0)){
            return false;
        }
        byte[] entry = new byte[8];

        while (read(0, entry, 8) == 8){
            String oldfname = IO_System.unpackStr(entry, 0).trim();
            int descIdx = IO_System.unpack(entry, 4);

            //if there exist file for destroy
            if (oldfname.equals(fname)){
                // find the file, over write
                lseek(0, oft[0].pos - 8);
                for (int i = 0; i < 8; i++){
                    entry[i] = 0;
                }
                write(0, entry, 8);

                // check if opened

                int openedIdx = -1;
                for (int i = 0; i < oft.length; i++){
                    if (oft[i] != null && oft[i].index == descIdx)
                        openedIdx = i;
                }
                // close opened file
                if (openedIdx >= 0){
                    oft[openedIdx] = null;
                }

                // read descriptor
                int[] desc = readDesc(descIdx);

                // clear bitmap
                byte[] tmp = new byte[IO_System.B];
                io.readBlock(0, tmp);
                for (int i = 1; i < desc.length; i++){
                    // valid blk
                    if (desc[i] > 0){
                        IO_System.setBit(tmp, false, desc[i]);
                    }
                    desc[i] = 0;
                }

                io.writeBlock(0, tmp);
                desc[0] = 0;
                writeDesc(descIdx, desc);

                return true;
            }
        }

            return false;
    }


    // the shell program
    public static void main(String[] args) throws IOException{
        Scanner sc = new Scanner(System.in);
        PrintStream out = System.out;
        File_System sys = new File_System();

        //use arg[0] as input file name if available.
        if (args.length > 0){
            sc = new Scanner(new File(args[0]));
        }

        while (sc.hasNextLine()){
            String line = sc.nextLine().trim();

            try
            {
                if (!line.isEmpty()){
                    Scanner scw = new Scanner(line);

                    String cmd = scw.next();

                    if (cmd.equals("in")){
                        // in <disk_cont.txt> load the file
                        String fname = "";
                        if (scw.hasNext()){
                            fname = scw.nextLine().trim();
                        }

                        if (sys.loadDisk(fname)){
                            out.println("disk restored");
                        }
                        else{
                            out.println("disk initialized");
                        }
                    }
                    else if (cmd.equals("sv")){
                        // sv <disk_cont.txt> save the file
                        String fname = scw.nextLine().trim();

                        if (sys.saveDisk(fname)){
                            out.println("disk saved");
                        }
                    }
                    else if (cmd.equals("op")){
                        // op <name> open the file
                        String fname = scw.nextLine().trim();
                        int idx = sys.open(fname);
                        if (idx >= 0){
                            out.println(fname + " opened " + idx);
                        }
                        else{
                            out.println("error");
                        }
                    }
                    else if (cmd.equals("cl")){
                        // cl <index>  close the file
                        int idx = scw.nextInt();
                        if (idx <= 0 || !sys.close(idx)){
                            out.println("error");
                        }
                        else{
                            out.println(idx + " closed");
                        }
                    }
                    else if (cmd.equals("rd")){
                        // rd <index> <count> read the data
                        int idx = scw.nextInt();
                        int cnt = scw.nextInt();

                        if (cnt < 0){
                            out.println("error");
                        }
                        else{
                            byte[] data = new byte[cnt];
                            if (sys.read(idx, data, cnt) >= 0){
                                out.println(new String(data));
                            }
                            else{
                                out.println("error");
                            }
                        }
                    }
                    else if (cmd.equals("sk")){
                        // sk <index> <pos>  seek to position
                        int idx = scw.nextInt();
                        int pos = scw.nextInt();

                        if (sys.lseek(idx, pos)){
                            out.println("position is " + pos);
                        }
                        else{
                            out.println("error");
                        }
                    }
                    else if (cmd.equals("dr")){
                        // directory
                        String result = sys.directory();
                        if (result == null){
                            out.println("error");
                        }
                        else{
                            out.println(result);
                        }
                    }
                    else if (cmd.equals("wr")){
                        // wr <index> <char> <count>  write the data
                        int idx = scw.nextInt();
                        char ch = scw.next().charAt(0);
                        int cnt = scw.nextInt();

                        if (cnt < 0 || idx <= 0){
                            out.println("error");
                        }
                        else{
                            byte[] data = new byte[cnt];
                            for (int i = 0; i < cnt; i++){
                                data[i] = (byte)ch;
                            }
                            if (sys.write(idx, data, cnt)){
                                out.println(cnt + " bytes written");
                            }
                            else{
                                out.println("error");
                            }
                        }
                    }
                    else if (cmd.equals("cr")){
                        // cr <name> create the file
                        String fname = scw.nextLine().trim();
                        if (sys.create(fname)){
                            out.println(fname + " created");
                        }
                        else{
                            out.println("error");
                        }
                    }
                    else if (cmd.equals("de")){
                        // de <name> destroy the file
                        String fname = scw.nextLine().trim();
                        if (sys.destroy(fname)){
                            out.println(fname + " destroyed");
                        }
                        else{
                            out.println("error");
                        }
                    }

                    else
                    {
                        out.println("error");
                    }

                }
                else{
                    out.println();
                }
            } catch (Exception e){
                out.println("error");
            }
        }


    }


}

