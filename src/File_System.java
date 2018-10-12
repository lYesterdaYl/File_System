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

    // the shell program
    public static void main(String[] args){
        Scanner sc = new Scanner(System.in);
        PrintStream out = System.out;
        File_System sys = new File_System();


    }


}

