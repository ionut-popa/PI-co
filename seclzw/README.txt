Linux
===== 

Building:
# make ./Makefile.linux 

Compress:
# ./seclzw -c ./samples/gps.delta.csv ./samples/gps.delta.csv.sec 

Decompress:
# ./seclze -c ./samples/gps.delta.csv.sec ./samples/gps.delta.csv.orig


WindRiver Rocket
================

Using Helix App Cloud - create an empty C project 
Upload seclzw-project tarball and unpack or git clone.
Open a console in the project root directory:

# cd ./seclzw-project
# ./setup-rocket.sh 

Build & Test your application in the emulated environment or on a real board.

The ./setup-rocket.sh script will translate all the data samples into C arrays.
main.c contains test function calls for data samples (add mode samples to the ./samples/ directory, re-run 
./setup-rocket.sh and update main.c accordingly to perform more tests)

Enjoy!


The algorithm
=============
The algorithm is a combination of LZW algorithm (also used in UNIX compress tool 
https://en.wikipedia.org/wiki/Compress) and a context model approach 
(as described in "SE-Compression: A Generalization of Dictionary-Based Compression" 
http://comjnl.oxfordjournals.org/content/early/2011/05/17/comjnl.bxr046)

The compression method is efficient for redundant data (more efficient than gzip)
but less efficient for less redundant data.

HINT: data obtained from sensors usually contains small deviations. By preprocessing the data with 
a delta encoding filter (e.g. new_data[i] = old_data[i] - old_data[i-1]) usually the signal is compressed
better.
Check utility script:
./utils/csv_column_delta.sh 


Key Features
============
1. very low complexity (both compression & decompression are implemented in just a few hundred lines 
of ANCI C code ~500)

2. Compression efficiency
- More efficient than UNIX Compress
- More efficient than LZ4
- Comparable to gzip (deflate) - for some data sets with hight correlation the SEC-LZW algorithm is more efficient,
for general data gzip -9 is more efficient; for general data gzip -1 is less efficient or comparable;  

3. Compression time
- Less efficient than compress, lz4 
- Comparable to gzip 
- More efficient than bzip2 or lzma


Sample data sources
===================
#1. San Francisco Wind Monitoring Data - Current 
#https://data.sfgov.org/Energy-and-Environment/San-Francisco-Wind-Monitoring-Data-Current/bkgs-xaqe
./2016/*

#2. Introduction How to Analyze Machine and Sensor Data
#http://hortonworks.com/hadoop-tutorial/how-to-analyze-machine-and-sensor-data/
./samples/HVAC.csv

#3. Personal recording
./samples/gps.csv  
./samples/gps.delta.csv  
./samples_images/*

#4. https://github.com/caroljmcdonald/SparkStreamingHBaseExample/blob/master/data/sensordata.csv
./samples/sensordata.csv

#5.UNKNOWN
./samples/weather.csv
./samples/walk.csv 

#6.T-Drive trajectory data sample  http://research.microsoft.com/apps/pubs/?id=152883
./gps/*

#7. http://sun.aei.polsl.pl/~rstaros/mednat/mednat-medical.zip
./medical/

#8. https://data.sparkfun.com/streams

Further improvements
====================
- Improve data structures
- Improve dictionary search algorithm (with the cost of increasing implementation complexity)
- Improve how dictionary pointers are encoded (entropy coder vs. fixed size block integer storage)
