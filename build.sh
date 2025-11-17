# Author: Santiago Ontañón (2025)
# Build file for the PAKET engine compiler.
# Note: this script does not assume that you use any build tool, like ant, to minimize dependencies. All you need is the java compiler.

# Create output folders:
echo "Creating target folders..."
mkdir -p target
mkdir -p target/classes
rm -rf target/classes/*

# Compile the Java files:
echo "Compiling Java source files..."
javac -sourcepath ./src -classpath ./lib/mdl.jar:./lib/jdom.jar -d target/classes src/paket/compiler/*.java
javac -sourcepath ./src -classpath ./lib/mdl.jar:./lib/jdom.jar -d target/classes src/paket/music/*.java
javac -sourcepath ./src -classpath ./lib/mdl.jar:./lib/jdom.jar -d target/classes src/paket/pak/*.java
javac -sourcepath ./src -classpath ./lib/mdl.jar:./lib/jdom.jar -d target/classes src/paket/platforms/*.java
javac -sourcepath ./src -classpath ./lib/mdl.jar:./lib/jdom.jar -d target/classes src/paket/text/*.java
javac -sourcepath ./src -classpath ./lib/mdl.jar:./lib/jdom.jar -d target/classes src/paket/tiles/*.java
javac -sourcepath ./src -classpath ./lib/mdl.jar:./lib/jdom.jar -d target/classes src/paket/util/*.java
javac -sourcepath ./src -classpath ./lib/mdl.jar:./lib/jdom.jar -d target/classes src/paket/util/cpc2cdt/*.java
javac -sourcepath ./src -classpath ./lib/mdl.jar:./lib/jdom.jar -d target/classes src/paket/util/idsk/*.java
javac -sourcepath ./src -classpath ./lib/mdl.jar:./lib/jdom.jar -d target/classes src/paket/util/zx0/*.java

# Generate the .jar file:
echo "Generating JAR file..."
cd target/classes
jar cf ../PAKET.jar .
cd ../..
cd src
jar uf ../target/PAKET.jar paket/templates
cd ..

# Add the mdl/jdom depenedncies
cd lib
mkdir tmp
unzip -q mdl.jar -d tmp
cd tmp
jar uf ../../target/PAKET.jar .
cd ..
rm -rf tmp

mkdir tmp
unzip -q jdom.jar -d tmp
cd tmp
jar uf ../../target/PAKET.jar .
cd ..
rm -rf tmp

cd ..

jar umf src/manifest.txt target/PAKET.jar

cd ..


