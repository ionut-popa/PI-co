
echo "//GENERATED FILE" > test_data.h
for X in `ls ../../../samples/`
do
    FILE_PATH="../../../samples/"$X
    STRUCT_NAME=`echo $X | sed 's/\./_/g'`
    echo "Processing file" $FILE_PATH " to struct:" $STRUCT_NAME

    echo "char $STRUCT_NAME[] = {" >> test_data.h
    hexdump -v -e '16/1 "0x%x," "\n"' $FILE_PATH | sed 's/0x,//g' >> test_data.h
    echo "};" >> test_data.h
done

#echo "char gps_delta[] = {" >> test_data.h
#hexdump -v -e '16/1 "0x%x," "\n"' ../../../samples/gps.delta.csv | sed 's/0x,//g' >> test_data.h
#echo "};" >> test_data.h

