package com.nuvio.tv.football

import kotlin.math.abs

data class PitchMarker(val player: FootballPlayer,val x: Float,val y: Float)
internal data class PitchPlacement(
    val markers: List<PitchMarker>,val unpositioned: List<FootballPlayer>,
    val markerWidth: Float,val markerHeight: Float,
)

/** Preserve the provider's formation. Resize whole markers instead of moving players between lines. */
internal fun pitchPlacement(players: List<FootballPlayer>,away: Boolean,width: Float,height: Float): PitchPlacement {
    val positioned=players.filter { p -> listOf(p.pitchX,p.pitchY).all { it!=null && it.isFinite() && it in 0f..1f } }
    val columns=mutableListOf<MutableList<FootballPlayer>>()
    for(player in positioned.sortedBy { it.pitchX }) {
        val previous=columns.lastOrNull()
        if(previous!=null && abs(previous.first().pitchX!!-player.pitchX!!)<.04f) previous+=player
        else columns+=mutableListOf(player)
    }
    val xGaps=columns.map { row -> row.map { it.pitchX!! }.average().toFloat() }.zipWithNext { a,b -> b-a }
    val yGaps=columns.flatMap { row -> row.map { it.pitchY!! }.sorted().zipWithNext { a,b -> b-a } }
    fun fit(default: Float,span: Float,gaps: List<Float>): Float {
        val gap=gaps.filter { it>.001f }.minOrNull() ?: return default.coerceAtMost(span)
        // Centres remain at normalized coordinates inside the complete marker bounds.
        return minOf(default,(span*gap-4f)/(1f+gap)).coerceAtLeast(0f)
    }
    val markerWidth=fit(56f,width,xGaps)
    val markerHeight=fit(48f,height,yGaps)
    if(markerWidth<36f || markerHeight<40f) return PitchPlacement(emptyList(),players,markerWidth,markerHeight)
    val markers=mutableListOf<PitchMarker>()
    for(player in positioned) {
        val x=(if(away) 1-player.pitchX!! else player.pitchX!!)*(width-markerWidth)
        val y=(if(away) 1-player.pitchY!! else player.pitchY!!)*(height-markerHeight)
        if(markers.any { abs(it.x-x)<markerWidth+1 && abs(it.y-y)<markerHeight+1 }) {
            // Invalid/duplicate coordinates are a list fallback, never an invented tactical position.
            continue
        } else markers+=PitchMarker(player,x,y)
    }
    return PitchPlacement(markers,players.filter { player -> markers.none { it.player===player } },markerWidth,markerHeight)
}
