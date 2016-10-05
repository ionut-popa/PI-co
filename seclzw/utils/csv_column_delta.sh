echo "Usage: $0 <input.csv> <column> <output.csv>"
echo "       Parse the CSV file, extract one specific column, "
echo "       then for this column calculate the delta between consecutive values"
echo "       (the first order derivate)"

cut -d"," -f${2} ${1} | awk 'BEGIN{X=0;FS=","}{print $1-X;X=$1}' > ${3}
