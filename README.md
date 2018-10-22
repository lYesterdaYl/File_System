# File_System
This is a File System project for my operating system project class

## Author

* Zhiyuan Du

## List of Commands

* cr name
    * create a new file with the name
    
* de name
    * destroy the named file

* op name
    * open the named file for reading and writing; display an index value

* cl index
    * lose the specified file
    
* rd index count
    * sequentially read count number of characters from the specified file index and display them on the terminal
    
* wr index char count
    * sequentially write count number of <char>s into the specified file index at its current position

* sk index pos
    * seek: set the current position of the specified file index to pos

* dr
    * directory: list the names of all files
    
* in disk_cont.txt
    *  create a disk using the prescribed dimension parameters and initialize it; also open directory
* sv disk_cont.txt
    * close all files and save the contents of the disk in the specified file