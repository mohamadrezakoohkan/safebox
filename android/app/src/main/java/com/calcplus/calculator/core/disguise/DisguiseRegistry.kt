package com.calcplus.calculator.core.disguise

/**
 * The compiled-in list of lock faces, in registry order (§1.6).
 *
 * Append-only: a shipped face is never removed, because an install enrolled on
 * it would otherwise become unopenable (skeleton §10 risk 4). The first entry
 * is the default and the fail-closed target.
 */
class DisguiseRegistry(val faces: List<DisguiseProvider>) {
    init {
        require(faces.isNotEmpty()) { "the registry needs at least the default face" }
        require(faces.map { it.id }.toSet().size == faces.size) { "disguise ids must be unique" }
    }

    /** Calculator — what an unresolvable id renders as, in `disguise` mode. */
    val default: DisguiseProvider = faces.first()

    /**
     * Fail closed: a missing, unknown or undecodable id resolves to [default].
     * Never an error surface, never a non-disguise surface (§3).
     */
    fun resolve(id: String?): DisguiseProvider =
        faces.firstOrNull { it.id == id } ?: default
}
