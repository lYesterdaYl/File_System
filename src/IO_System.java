import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;


public class IO_System{
    public static final int L = 64;   // 64 blocks
    public static final int B = 64;   // 64 bytes per block

    byte[][] ldisk;

    // constructor, create the ldisk
    public IO_System(){
        ldisk = new byte[L][B];
        clear();
    }

    public void clear(){
        for (int i = 0; i < L; i++)
            for (int j = 0; j < B; j++)
                ldisk[i][j] = 0;
    }

    public void readBlock(int i, byte[] p){
        for (int j = 0; j < B; j++){
            p[j] = ldisk[i][j];
        }
    }

    public void writeBlock(int i, byte[] p){
        for (int j = 0; j < B; j++){
            ldisk[i][j] = p[j];
        }
    }

    // save disk to a file
    public void saveFile(String fname) throws IOException{
        FileOutputStream file = new FileOutputStream(fname);
        for (int i = 0; i < L; i++){
            file.write(ldisk[i]);
        }
        file.close();
    }

    // load disk from a file
    public void loadFile(String fname) throws IOException{
        FileInputStream file = new FileInputStream(fname);
        for (int i = 0; i < L; i++){
            if (file.read(ldisk[i]) != B){
                file.close();
                throw new IOException("Error reading");
            }
        }
        file.close();
    }


    // some utility functions

    // pack int into byte array
    public static void pack(byte[] mem, int val, int loc){
        final int MASK = 0xff;
        for (int i = 3; i >= 0; i--){
            mem[loc+i] = (byte)(val & MASK);
            val = val >> 8;
        }
    }

    // unpack int from array
    public static int unpack(byte[] mem, int loc){
        final int MASK = 0xff;
        int v = (int)mem[loc] & MASK;
        for (int i = 1; i < 4; i++){
            v = v << 8;
            v = v | ((int)mem[loc+i] & MASK);
        }
        return v;
    }

    // pack int into byte array
    public static void packArr(byte[] mem, int[] val, int loc){
        for (int i=0; i < val.length; i++){
            pack(mem, val[i], loc + i * 4);
        }
    }

    // unpack int from array
    public static int[] unpackArr(byte[] mem, int loc, int size){
        int[] val = new int[size];
        for (int i=0; i < size; i++){
            val[i] = unpack(mem, loc + i * 4);
        }
        return val;
    }

    public static void packStr(byte[] mem, String val, int loc){
        byte[] d = val.getBytes();
        for (int i = 0; i < 4; i++){
            byte c = 0;
            if (i < d.length)
                c = d[i];
            mem[loc+i] = c;
        }
    }

    // unpack int from array
    public static String unpackStr(byte[] mem, int loc){
        byte[] tmp = new byte[4];
        for (int i=0; i < 4; i++){
            tmp[i] = mem[loc+i];
        }
        return new String(tmp).trim();
    }

    // set and get the bit map
    public static boolean getBit(byte[] data, int idx){
        int pos = idx / 8;
        int off = idx % 8;  // 0-7

        byte v = data[pos];
        return (v & (1<<off)) != 0;
    }

    public static void setBit(byte[] data, boolean val, int idx){
        int pos = idx / 8;
        int off = idx % 8;  // 0-7

        if (val)
            data[pos] = (byte)(data[pos] | (1<<off) );
        else
            data[pos] = (byte)(data[pos] & (~(1<<off)));
    }

}

