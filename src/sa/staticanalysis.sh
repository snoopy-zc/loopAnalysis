# Input args
# $1 - dir of jars

# Please set the project home directory
project_dir=/home/snoopy/workspace/loopAnalysis             #JX - NO "/" at the end

# Compile sa
echo "JX - INFO - compile sa .."
cd $project_dir
ant compile-sa
if [ $? -ne 0 ]; then
  echo "compile error"
  exit
fi

# Wala + RPC
echo "JX - INFO - Static Analysis NOW .."
build_path=${project_dir}/build/classes/
wala_path=${project_dir}/lib/sa/wala-1.3.8-jars/*
classpath=$build_path:$wala_path
cd $project_dir        
java -Xmx16G -cp $classpath sa.StaticAnalysis ${project_dir} $1
