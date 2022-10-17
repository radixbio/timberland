load("@rules_pkg//:pkg.bzl", "pkg_deb", "pkg_tar")

PROGUARD_DEFAULT = """-dontoptimize
                      -dontobfuscate
                      -dontnote
                      # this is probably a bad idea...
                      # ideally would be -dontwarn scala.**
                      -dontwarn
                      #-printmapping out.map
                      -libraryjars <java.home>/jmods/java.base.jmod(!**.jar;!module-info.class)
                      -renamesourcefileattribute SourceFile
                      -keepattributes SourceFile,LineNumberTable

                      -keepattributes *Annotation*

                      # Preserve all public applications.
                      -keepclasseswithmembers public class * {
                          public static void main(java.lang.String[]);
                      }
                      # Preserve some classes and class members that are accessed by means of
                      # introspection.
                      -keep class * implements org.xml.sax.EntityResolver

                      -keepclassmembers class * {
                          ** MODULE$;
                      }

                      -keepclassmembernames class scala.concurrent.forkjoin.ForkJoinPool {
                          long eventCount;
                          int  workerCounts;
                          int  runControl;
                          scala.concurrent.forkjoin.ForkJoinPool$WaitQueueNode syncStack;
                          scala.concurrent.forkjoin.ForkJoinPool$WaitQueueNode spareStack;
                      }


                      -keepclassmembernames class scala.concurrent.forkjoin.ForkJoinWorkerThread {
                          int base;
                          int sp;
                          int runState;
                      }

                      -keepclassmembernames class scala.concurrent.forkjoin.ForkJoinTask {
                          int status;
                      }

                      -keepclassmembernames class scala.concurrent.forkjoin.LinkedTransferQueue {
                          scala.concurrent.forkjoin.LinkedTransferQueue$PaddedAtomicReference head;
                          scala.concurrent.forkjoin.LinkedTransferQueue$PaddedAtomicReference tail;
                          scala.concurrent.forkjoin.LinkedTransferQueue$PaddedAtomicReference cleanMe;
                      }
                      -keepclasseswithmembernames,includedescriptorclasses class * {
                          native <methods>;
                      }



                      -keep class com.typesafe.**
                      -keep class akka.**
                      -keep class scala.collection.immutable.StringLike {
                          *;
                      }
                      -keepclasseswithmembers class * {
                          public <init>(java.lang.String, akka.actor.ActorSystem$Settings, akka.event.EventStream, akka.actor.Scheduler, akka.actor.DynamicAccess);
                      }
                      -keepclasseswithmembers class * {
                          public <init>(akka.actor.ExtendedActorSystem);
                      }
                      -keep class scala.collection.SeqLike {
                          public protected *;
                      }

                      # Preserve the special static methods that are required in all enumeration
                      # classes.

                      -keepclassmembers,allowoptimization enum * {
                          public static **[] values();
                          public static ** valueOf(java.lang.String);
                      }

                      # Explicitly preserve all serialization members. The Serializable interface
                      # is only a marker interface, so it wouldn't save them.
                      # You can comment this out if your application doesn't use serialization.
                      # If your code contains serializable classes that have to be backward
                      # compatible, please refer to the manual.

                      -keepclassmembers class * implements java.io.Serializable {
                          static final long serialVersionUID;
                          static final java.io.ObjectStreamField[] serialPersistentFields;
                          private void writeObject(java.io.ObjectOutputStream);
                          private void readObject(java.io.ObjectInputStream);
                          java.lang.Object writeReplace();
                          java.lang.Object readResolve();
                      }
                      -keep class akka.**
                      -keep interface akka.**
                      -keep enum akka.**
                      -keep class com.radix.**
                      -keep interface com.radix.**
                      -keep enum com.radix.**
                      """

def proguardify(
        name,
        srcs = [],
        proguard_script = PROGUARD_DEFAULT):
    """
    Runs proguard for a fat jar file in order to slim it down
    """

    native.genrule(
        name = name + "-proguardify",
        outs = [name + "_slim.jar"],
        srcs = srcs,
        cmd = """
        echo \"""" + PROGUARD_DEFAULT.replace("$", "\$$") + """ " >> args.pro && """ +
              "$(location @proguard//:proguard) @args.pro -injars $(SRCS) -outjars out.jar &&" +
              "mv out.jar $@",
        tools = ["@proguard//:proguard"],
    )

def services_pkg_tar(name, srcs, package_dir, use_proguard = False):
    """
    Runs pkg_tar, optionally running proguard on all the passed srcs, and renames the outputs to look like "foo-bin.jar"
    """
    proguarded_srcs = []
    if use_proguard:
        for src in srcs:
            src_name = src.split(":")[-1].replace("_deploy.jar", "")
            proguardify(
                name = src_name + "-pro",
                srcs = [src],
            )
            proguarded_srcs.append(":" + src_name + "-pro_slim.jar")
    else:
        proguarded_srcs = srcs

    remap_paths = {}
    for src in proguarded_srcs:
        src_file = src.split(":")[-1]
        dest_file = src_file.replace("-pro_slim", "").replace("_deploy", "")
        remap_paths["/" + src_file] = dest_file

    pkg_tar(
        name = name,
        srcs = proguarded_srcs,
        package_dir = package_dir,
        remap_paths = remap_paths,
    )
