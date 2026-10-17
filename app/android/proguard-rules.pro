# R8 rules for the release and internal builds. Libraries ship their own consumer rules; add a rule
# here only for something R8 can't see, and say why.

# Readable stack traces in Drop crash reports and diagnostics.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
