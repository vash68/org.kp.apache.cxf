2016-2-2 ivashkulat

Place settings.xml into a directory where your maven is loading it from!
It can be MAVEN_HOME/conf or, for example, I use .m2 directory next to my local repository directory for clarity.


 Now you can use "deploy" target in cxf root pom to deploy ALL cxf artifacts into Kaiser Archiva maven repo.