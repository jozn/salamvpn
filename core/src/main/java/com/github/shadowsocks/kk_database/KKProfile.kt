package com.github.shadowsocks.kk_database

data class KKProfile(
    var id: Long = 0,
    var name: String? = "",
    var host: String = "example.shadowsocks.org",
    var remotePort: Int = 8388,
    var password: String = "u1rRWTssNv0p",
    var method: String = "aes-256-cfb",
    var route: String = "all",
    var remoteDns: String = "dns.google",
    var proxyApps: Boolean = false,
    var bypass: Boolean = false,
    var udpdns: Boolean = false,
    var ipv6: Boolean = false,
    var metered: Boolean = false,
    var individual: String = "",
    var plugin: String? = null,
    var udpFallback: Long? = null,
    var tx: Long = 0,
    var rx: Long = 0,
    var userOrder: Long = 0,
    var dirty: Boolean = false
) {
    val formattedAddress: String get() = (if (host.contains(":")) "[%s]:%d" else "%s:%d").format(host, remotePort)
    val formattedName: String get() = name ?: formattedAddress
}

object KKProfileDB {
    private val profiles = mutableMapOf<Long, KKProfile>()
    private val sampleProfiles = generateSampleProfiles()

    private fun generateSampleProfiles(): List<KKProfile> {
        val jsonPort = 443
        val jsonPassword = "ePopTvLs2X3jy"
        val jsonMethod = "aes-256-gcm"
        val ip = "91.107.143.205"
        val countries = listOf("US", "CA", "DE", "FR", "GB", "JP", "AU", "IN", "CN", "BR")
        val profiles = mutableListOf<KKProfile>()
        for (country in countries) {
            val serverCount = (5..15).random()
            for (i in 1..serverCount) {
                profiles.add(
                    KKProfile(
                        name = "$country-Server-$i",
                        host = ip,
                        remotePort = jsonPort,
                        password = jsonPassword,
                        method = jsonMethod
                    )
                )
            }
        }
        return profiles
    }

    fun get(id: Long): KKProfile? {
        return profiles[id]
    }

    fun getNonNull(id: Long): KKProfile {
        return profiles[id] ?: throw IllegalArgumentException("Profile with ID $id not found")
    }

    fun listActive(): List<KKProfile> {
        return sampleProfiles
    }

    fun listAll(): List<KKProfile> {
        return profiles.values.toList()
    }

    fun nextOrder(): Long {
        return profiles.values.map { it.userOrder }.maxOrNull() ?: 0L + 1
    }

    fun isNotEmpty(): Boolean {
        return profiles.isNotEmpty()
    }

    fun create(value: KKProfile): Long {
        val id = profiles.size.toLong() + 1
        value.id = id
        profiles[id] = value
        return id
    }

    fun update(value: KKProfile): Boolean {
        return if (profiles.containsKey(value.id)) {
            profiles[value.id] = value
            true
        } else {
            false
        }
    }

    fun delete(id: Long): Boolean {
        return profiles.remove(id) != null
    }

    fun deleteAll(): Int {
        val size = profiles.size
        profiles.clear()
        return size
    }
}


fun generateSampleProfiles(): List<KKProfile> {
    // Extract necessary data from the JSON
    val jsonHost = "0.0.0.0"
    val jsonPort = 443
    val jsonPassword = "ePopTvLs2X3jy"
    val jsonMethod = "aes-256-gcm"

    // Static IP for all profiles
    val ip = "91.107.143.205"

    val countries = listOf("US", "CA", "DE", "FR", "GB", "JP", "AU", "IN", "CN", "BR")

    val profiles = mutableListOf<KKProfile>()
    for (country in countries) {
        val serverCount = (5..15).random()  // Randomly decide number of servers for each country
        for (i in 1..serverCount) {
            profiles.add(
                KKProfile(
                    name = "$country-Server-$i",
                    host = ip,
                    remotePort = jsonPort,
                    password = jsonPassword,
                    method = jsonMethod
                )
            )
        }
    }
    return profiles
}

val sampleProfiles = generateSampleProfiles()

data class KKExpandedProfile(val main: KKProfile, val udpFallback: KKProfile?) {
    fun toList() = listOfNotNull(main, udpFallback)
}

fun getKKProfileById(id: Long): KKProfile? {
    // Mock function to get KKProfile by its ID. You will need to replace this with actual functionality.
    return null
}

fun expandProfile(profile: KKProfile): KKExpandedProfile {
    // Mock function to expand KKProfile to KKExpandedProfile. You will need to adjust this.
    return KKExpandedProfile(main = profile, udpFallback = null)
}