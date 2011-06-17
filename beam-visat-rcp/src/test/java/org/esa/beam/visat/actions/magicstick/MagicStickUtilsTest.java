package org.esa.beam.visat.actions.magicstick;

import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Norman Fomferra
 */
public class MagicStickUtilsTest {

    private Product product;
    private Band b1;
    private Band b2;
    private Band b3;

    @Before
    public void setUp() throws Exception {
        product = new Product("product", "t", 16, 16);
        product.addBand("a1", ProductData.TYPE_FLOAT32);
        b1 = product.addBand("b1", ProductData.TYPE_FLOAT32);
        product.addBand("a2", ProductData.TYPE_FLOAT32);
        b2 = product.addBand("b2", ProductData.TYPE_FLOAT32);
        product.addBand("a3", ProductData.TYPE_FLOAT32);
        b3 = product.addBand("b3", ProductData.TYPE_FLOAT32);
        b1.setSpectralWavelength(100);
        b2.setSpectralWavelength(200);
        b3.setSpectralWavelength(300);
    }

    @Test
    public void testGetSpectralBands() throws Exception {
        Band[] bands = MagicStickUtils.getSpectralBands(product);
        assertEquals(3, bands.length);
        assertSame(bands[0], b1);
        assertSame(bands[1], b2);
        assertSame(bands[2], b3);
    }


    @Test
    public void testCreateExpressionWith3Bands() throws Exception {

        Band[] bands = {
                b1, b2, b3
        };
        double[] spectrum = {
                0.4, 0.3, 0.2
        };
        double tolerance = 0.1;
        String expression = MagicStickUtils.createExpression(bands, spectrum, tolerance);

        assertEquals("sqrt((b1-0.4)*(b1-0.4)+(b2-0.3)*(b2-0.3)+(b3-0.2)*(b3-0.2)) < 0.1", expression);
    }

    @Test
    public void testCreateExpressionWith1Band() throws Exception {

        Band[] bands = {
                product.getBand("a2")
        };
        double[] spectrum = {
                0.2
        };
        double tolerance = 0.05;
        String expression = MagicStickUtils.createExpression(bands, spectrum, tolerance);

        assertEquals("sqrt((a2-0.2)*(a2-0.2)) < 0.05", expression);
    }
}
