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

    // read data from the file
    public boolean read(int index, byte[] data, int count){
        if (index < 0 || index > oft.length){
            return false;
        }
        if (oft[index] == null){
            return false;
        }

        // get the file descriptor
        int[] desc = readDesc(oft[index].index);
        if (oft[index].pos + count > desc[0]){
            return false;
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

        return true;
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

        return true;
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

