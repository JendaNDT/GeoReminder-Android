# Pravidla pro R8/ProGuard.
# Většina knihoven (kotlinx.serialization, Compose, Glance, Play services)
# si své keep-rules dodává sama; níže je pojistka pro naše serializovatelné
# modely, aby minifikace nerozbila čtení/zápis JSON připomínek.

# --- kotlinx.serialization ---
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class **$$serializer { *; }

# --- Naše datové modely (jistota navíc) ---
-keep class cz.jenda.georeminder.model.** { *; }
-keep class cz.jenda.georeminder.model.**$$serializer { *; }
