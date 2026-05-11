package com.gamegear.network

object GameImageUrl {
    fun cover(ref: String): String =
        if ("://" in ref) ref else IgdbImageUrl.coverBig(ref)

    fun thumbnail(ref: String): String = when {
        ref.contains("cdn.thegamesdb.net/images/large/") ->
            ref.replace("/images/large/", "/images/thumb/")
        ref.contains("cdn.thegamesdb.net/images/original/") ->
            ref.replace("/images/original/", "/images/thumb/")
        "://" in ref -> ref
        else -> IgdbImageUrl.thumbnail(ref)
    }
}
