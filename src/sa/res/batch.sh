#!/bin/bash
for file in ./*
do
	if test -d $file
	then
		if [[ "$file" != "./io" ]] 
		then
			echo $file
			./../staticanalysis.sh $(pwd)/$file > ./${file}_res.txt
		fi
	fi
done
