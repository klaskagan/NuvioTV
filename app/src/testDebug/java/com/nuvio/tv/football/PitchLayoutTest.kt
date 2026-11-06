package com.nuvio.tv.football

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

/** Regression coordinates from the requested Fenerbahce/Liverpool live team sheets, without result data. */
class PitchLayoutTest {
    private fun players(columns: List<Pair<Float,List<Float>>>): List<FootballPlayer> = columns.flatMap { (x,ys) ->
        ys.map { y -> FootballPlayer("Original Player","",x,y) }
    }
    private val home=players(listOf(.1f to listOf(.5f),.36f to listOf(.88f,.63f,.38f,.13f),
        .61f to listOf(.79f,.5f,.21f),.87f to listOf(.79f,.5f,.21f)))
    private val away=players(listOf(.1f to listOf(.5f),.29f to listOf(.88f,.63f,.38f,.13f),
        .49f to listOf(.7f,.3f),.68f to listOf(.84f,.5f,.16f),.87f to listOf(.5f)))
    @Test fun realFourThreeThreeAndFourTwoThreeOneRetainTheirExactFormationLinesAtTvSizes() {
        for(width in listOf(264f,352f,528f)) for((team,rotated) in listOf(home to false,away to true)) {
            val height=width*2*68/105
            val placed=pitchPlacement(team,rotated,width,height)
            assertEquals(11,placed.markers.size);assertTrue(placed.unpositioned.isEmpty())
            for(marker in placed.markers) {
                val expectedX=(if(rotated) 1-marker.player.pitchX!! else marker.player.pitchX!!)*(width-placed.markerWidth)
                val expectedY=(if(rotated) 1-marker.player.pitchY!! else marker.player.pitchY!!)*(height-placed.markerHeight)
                assertEquals(expectedX,marker.x,.001f);assertEquals(expectedY,marker.y,.001f)
                assertTrue(marker.x>=0 && marker.x+placed.markerWidth<=width+.001f)
                assertTrue(marker.y>=0 && marker.y+placed.markerHeight<=height+.001f)
            }
            for(i in placed.markers.indices) for(j in i+1 until placed.markers.size) {
                assertTrue(abs(placed.markers[i].x-placed.markers[j].x)>=placed.markerWidth ||
                    abs(placed.markers[i].y-placed.markers[j].y)>=placed.markerHeight)
            }
            assertEquals(if(rotated) listOf(1,3,2,4,1) else listOf(1,4,3,3),
                placed.markers.groupBy { (it.x*100).toInt() }.toSortedMap().values.map { it.size })
        }
    }
    @Test fun denseFiveColumnFormationScalesMarkersInsteadOfMovingPlayersOntoArbitraryGrid() {
        val placed=pitchPlacement(away,false,264f,342f)
        assertTrue(placed.markerWidth<44f)
        assertEquals(5,placed.markers.map { it.x }.distinct().size)
        assertEquals(1,placed.markers.filter { it.player.pitchX==.49f }.map { it.x }.distinct().size)
    }
    @Test fun duplicateOrMissingCoordinatesUseListFallbackWithoutInventingPositions() {
        val first=FootballPlayer("Original Keeper","1",.1f,.5f)
        val duplicate=FootballPlayer("Duplicate coordinate","2",.1f,.5f)
        val missing=FootballPlayer("Missing coordinate","3",null,null)
        val placed=pitchPlacement(listOf(first,duplicate,missing),false,264f,342f)
        assertEquals(listOf(first),placed.markers.map { it.player })
        assertEquals(listOf(duplicate,missing),placed.unpositioned)
    }
}
