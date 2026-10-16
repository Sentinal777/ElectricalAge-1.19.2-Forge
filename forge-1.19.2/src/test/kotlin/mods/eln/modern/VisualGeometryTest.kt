package mods.eln.modern

import net.minecraft.core.Direction
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.test.*

class VisualGeometryTest {
    private fun part(id: String,face: Direction=Direction.DOWN)=PartState(PartDefinition.byId.getValue(id),face)

    @Test fun allNewFramesPreserveHandednessAndDepth() {
        for(face in Direction.values())for(rotation in 0..3) {
            val p=part("and_chip",face);p.rotation=rotation
            val origin=PartGeometry.localToBlock(p,.5,0.0,.5)
            val x=PartGeometry.localToBlock(p,1.5,0.0,.5).subtract(origin)
            val y=PartGeometry.localToBlock(p,.5,1.0,.5).subtract(origin)
            val z=PartGeometry.localToBlock(p,.5,0.0,1.5).subtract(origin)
            assertEquals(1.0,x.cross(y).dot(z),1e-12,"Reflected face $face / $rotation")
            assertEquals(Vec3.atLowerCornerOf(face.opposite.normal),y)
            val body=PartGeometry.body(p)
            assertTrue(body.xsize>0 && body.ysize>0 && body.zsize>0)
        }
    }
    @Test fun oldWorldsKeepPinDirectionsAndNewStateKeepsItsVersion() {
        for(face in Direction.values())for(rotation in 0..3) {
            val p=part("power_resistor",face);p.rotation=rotation
            val modern=PartState.load(p.save())!!
            assertEquals(2,modern.orientationVersion)
            val old=p.save();old.remove("orientationVersion")
            val legacy=PartState.load(old)!!
            assertEquals(FaceBasis.edges(face,rotation,1),FaceBasis.edges(legacy))
            assertEquals(1,PartState.load(legacy.save())!!.orientationVersion)
        }
    }
    @Test fun originalModelProportionsAndCableSizesArePreserved() {
        assertEquals(.125,PartGeometry.body(part("electrical_source")).ysize,1e-5)
        assertEquals(.125,PartGeometry.body(part("small_solar_panel")).ysize,1e-5)
        val thermal=part("copper_thermal_cable");thermal.connectedMask=5
        assertEquals(.25,PartGeometry.body(thermal).ysize,1e-5)
        assertTrue(thermal.definition.visual.cableTexture.endsWith("tex_thermalcablebase.png"))
        assertEquals(.75,PartGeometry.body(part("cost_oriented_battery")).ysize,1e-5)
    }
    @Test fun onlyConnectedEdgesContributeCableArms() {
        val p=part("low_voltage_cable")
        assertTrue(PartGeometry.localConnections(p).isEmpty())
        p.connectedMask=1 or 8
        val arms=PartGeometry.localConnections(p)
        assertEquals(2,arms.size)
        assertTrue(arms.any { it.maxX==1.0 });assertTrue(arms.any { it.minZ==0.0 })
        assertFalse(arms.any { it.minX==0.0 || it.maxZ==1.0 })
    }
    @Test fun bodySelectionDistinguishesTwoFacesInOneBlock() {
        val floor=part("and_chip")
        val wall=part("and_chip",Direction.NORTH)
        assertFalse(PartGeometry.overlapsBody(floor,wall))
        val point=Vec3(.5,.5,.02)
        assertTrue(PartGeometry.distanceTo(wall,point)<PartGeometry.distanceTo(floor,point))
        val tall=part("data_logger")
        assertTrue(PartGeometry.overlapsBody(tall,wall))
    }
    @Test fun everyNativePartHasOriginalArtworkAndFiniteGeometry() {
        for(d in PartDefinition.all) {
            if(d.customItemRendering) {
                val index=com.google.gson.JsonParser.parseReader(javaClass.getResourceAsStream("/assets/eln/model_index.json")!!.reader()).asJsonObject
                assertNotNull(javaClass.getResource("/assets/eln/"+index.get(d.model).asString),d.id)
                assertTrue(d.modelGroups.isNotEmpty())
            } else assertNotNull(javaClass.getResource("/assets/eln/textures/${d.icon}.png"),d.id)
            assertEquals(6,d.visual.bounds.size)
            // Wind blades exceed four blocks; the original transporter door reaches exactly four, and the tower nearly ten.
            val envelope=if(d.behavior in setOf(Behavior.WIND_TURBINE,Behavior.TELEPORTER))5 else if(d.grid==null)4 else when(d.id) { "transmission_tower" -> 10;"grid_switch" -> 6;else -> 5 }
            assertTrue(d.visual.bounds.all { it.isFinite() && abs(it)<envelope },d.id)
            assertTrue(d.model.isNotEmpty() || d.behavior in setOf(Behavior.CABLE,Behavior.SIGNAL_BUS,Behavior.THERMAL_CABLE,Behavior.FE_CONVERTER,Behavior.COMPUTER_PROBE),d.id)
            if(d.behavior==Behavior.COMPUTER_PROBE) {
                assertEquals(listOf(0.0,0.0,0.0,1.0,1.0,1.0),d.visual.bounds)
                for(face in listOf("xn","xp","yn","yp","zn","zp"))assertNotNull(javaClass.getResource("/assets/eln/textures/blocks/computerprobe_$face.png"),"Original cube texture $face")
            }
        }
    }
    @Test fun trackerIsHorizontalAtNoonAndRespectsOriginalStops() {
        val pi=kotlin.math.PI
        assertEquals(pi/2,SolarExposure.trackingAngle(0.0),1e-12)
        assertEquals(pi/4,SolarExposure.trackingAngle(3*pi/2),1e-12)
        assertEquals(3*pi/4,SolarExposure.trackingAngle(pi/2),1e-12)
    }

}
