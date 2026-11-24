package com.example.data.model

data class GamePackage(
    val id: String,
    val name: String,
    val version: String,
    val sizeBytes: Long,
    val description: String,
    val downloadUrl: String,
    val checksum: String,
    val isInitial: Boolean = false
) {
    val formattedSize: String
        get() {
            val mb = sizeBytes.toDouble() / (1024 * 1024)
            return String.format("%.2f MB", mb)
        }
}

data class PackageManifest(
    val gameVersion: String = "2.0",
    val title: String = "NEXO Battle Royale Base Update",
    val packages: List<GamePackage>
) {
    val totalSizeBytes: Long
        get() = packages.sumOf { it.sizeBytes }

    val formattedTotalSize: String
        get() {
            val mb = totalSizeBytes.toDouble() / (1024 * 1024)
            return String.format("%.2f MB", mb)
        }

    val initialPackage: GamePackage?
        get() = packages.firstOrNull { it.isInitial }

    val additionalPackages: List<GamePackage>
        get() = packages.filterNot { it.isInitial }

    companion object {
        fun defaultManifest(): PackageManifest {
            return PackageManifest(
                gameVersion = "2.0",
                title = "NEXO v2.0 Base Game Packages",
                packages = listOf(
                    GamePackage(
                        id = "pkg_core_engine_v01",
                        name = "Initial Core Game Resources",
                        version = "2.0.0",
                        sizeBytes = 2 * 1024 * 1024L, // Exact 2.0 MB initial package
                        description = "Core engine scripts, landscape UI shaders, and base network protocol",
                        downloadUrl = "https://cdn.nexo.example/v2.0/core_engine_v20.bin",
                        checksum = "e4d909c290d0fb1ca068ffaddf22cbd0",
                        isInitial = true
                    ),
                    GamePackage(
                        id = "pkg_character_assets_v01",
                        name = "NEXO Character Textures & Rigging",
                        version = "2.0.0",
                        sizeBytes = 1572864L, // 1.5 MB
                        description = "High-definition textures for the trio characters (Pink, Yellow, Teal)",
                        downloadUrl = "https://cdn.nexo.example/v2.0/character_assets_v20.bin",
                        checksum = "fa88c2f1f0a174092bbf180d12ee9e32",
                        isInitial = false
                    ),
                    GamePackage(
                        id = "pkg_audio_sfx_v01",
                        name = "Futuristic Battlefield Audio & SFX",
                        version = "2.0.0",
                        sizeBytes = 1258291L, // 1.2 MB
                        description = "Main theme soundtrack, weapon soundscapes and dynamic interface effects",
                        downloadUrl = "https://cdn.nexo.example/v2.0/audio_sfx_v20.bin",
                        checksum = "7d89cf49021da3b0a7b45fef10928a30",
                        isInitial = false
                    )
                )
            )
        }
    }
}
