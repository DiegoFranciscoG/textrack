# kotlinx.serialization: conserva los serializadores generados de los DTO.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class io.github.diegofranciscog.textrack.scanner.**$$serializer { *; }

# Retrofit: interfaces de servicio usadas por reflexión.
-keep,allowobfuscation interface io.github.diegofranciscog.textrack.scanner.data.remote.ApiService
-keepattributes Signature, Exceptions
