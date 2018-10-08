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
        for (int j = 0; j < B; j++)
        {
            p[j] = ldisk[i][j];
        }
    }

    public void writeBlock(int i, byte[] p){
        for (int j = 0; j < B; j++)
        {
            ldisk[i][j] = p[j];
        }
    }

    // save disk to a file
    public void saveFile(String fname) throws IOException{
        FileOutputStream file = new FileOutputStream(fname);
        for (int i = 0; i < L; i++)
            file.write(ldisk[i]);
        file.close();
    }

    // load disk from a file
    public void loadFile(String fname) throws IOException{
        FileInputStream file = new FileInputStream(fname);
        for (int i = 0; i < L; i++)
        {
            if (file.read(ldisk[i]) != B)
            {
                file.close();
                throw new IOException("Error reading");
            }
        }
        file.close();
    }


}

