package mods.eln.sim.mna

import org.apache.commons.numbers.core.DD
import java.math.BigDecimal
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

class MnaDotProductTest {
    @Test fun mixedScaleCancellationMatchesIndependentExactDecimalReference() {
        val random=Random(1246)
        repeat(80) { sample ->
            val row=ArrayList<DD>();val rhs=ArrayList<Double>()
            repeat(32) {
                val scale=Math.scalb(1.0,random.nextInt(-160,161))
                val a=DD.ofSum(scale*random.nextDouble(.5,1.0),scale*random.nextDouble(-1e-17,1e-17))
                val b=random.nextDouble(.1,2.0)
                row+=a;rhs+=b
                row+=DD.of(Math.scalb(scale,-65));rhs+=random.nextDouble(.1,2.0)
                row+=a.negate();rhs+=b
            }
            var exact=BigDecimal.ZERO;var largest=0.0
            for(i in row.indices) {
                val coefficient=BigDecimal(row[i].hi()).add(BigDecimal(row[i].lo()))
                val product=coefficient.multiply(BigDecimal(rhs[i]))
                exact=exact.add(product);largest=max(largest,abs(product.toDouble()))
            }
            val expected=exact.toDouble();val actual=MnaDotProduct.sum(row.toTypedArray(),rhs.toDoubleArray())
            val tolerance=max(8*Math.ulp(expected),largest*1e-29)
            assertTrue(abs(actual-expected)<=tolerance,"Exact reference mismatch $sample: $actual vs $expected; tolerance=$tolerance")
        }
    }
    @Test fun exceptionalValuesPreserveOriginalDdFaultPropagation() {
        for(value in listOf(Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.MAX_VALUE,Double.MIN_VALUE)) {
            for(b in listOf(0.0,1.0,-1.0,Double.MAX_VALUE,Double.MIN_VALUE)) {
                val row=arrayOf(DD.of(value),DD.of(1.0));val rhs=doubleArrayOf(b,2.0)
                val expected=row[0].multiply(b).add(row[1].multiply(2.0)).toDouble()
                val actual=MnaDotProduct.sum(row,rhs)
                if(expected.isNaN())assertTrue(actual.isNaN(),"Fault propagation changed for $value * $b")
                else assertTrue(expected==actual,"Exceptional value changed for $value * $b: $expected vs $actual")
            }
        }
    }
}
