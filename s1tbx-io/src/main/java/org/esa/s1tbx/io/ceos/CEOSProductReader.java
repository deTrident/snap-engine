/*
 * Copyright (C) 2015 by Array Systems Computing Inc. http://www.array.ca
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */
package org.esa.s1tbx.io.ceos;

import com.bc.ceres.core.ProgressMonitor;
import com.bc.ceres.core.VirtualDir;
import org.esa.s1tbx.io.SARReader;
import org.esa.snap.core.dataio.ProductReaderPlugIn;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.ProductData;
import org.esa.snap.engine_utilities.datamodel.Unit;
import org.esa.snap.engine_utilities.gpf.ReaderUtils;
import org.esa.snap.engine_utilities.util.ZipUtils;

import java.io.File;
import java.io.IOException;

/**
 * The product reader for Radarsat products.
 */
public abstract class CEOSProductReader extends SARReader {

    private VirtualDir productDir = null;
    protected CEOSProductDirectory dataDir = null;

    /**
     * Constructs a new abstract product reader.
     *
     * @param readerPlugIn the reader plug-in which created this reader, can be <code>null</code> for internal reader
     *                     implementations
     */
    protected CEOSProductReader(final ProductReaderPlugIn readerPlugIn) {
        super(readerPlugIn);
    }

    protected abstract CEOSProductDirectory createProductDirectory(final VirtualDir productDir);

    /**
     * Closes the access to all currently opened resources such as file input streams and all resources of this children
     * directly owned by this reader. Its primary use is to allow the garbage collector to perform a vanilla job.
     * <p/>
     * <p>This method should be called only if it is for sure that this object instance will never be used again. The
     * results of referencing an instance of this class after a call to <code>close()</code> are undefined.
     * <p/>
     * <p>Overrides of this method should always call <code>super.close();</code> after disposing this instance.
     *
     * @throws java.io.IOException if an I/O error occurs
     */
    @Override
    public void close() throws IOException {
        if (dataDir != null) {
            dataDir.close();
            dataDir = null;
        }
        super.close();
    }

    protected VirtualDir createProductDir(final File inputFile) {
        if (ZipUtils.isZip(inputFile)) {
            return VirtualDir.create(inputFile);
        } else {
            return VirtualDir.create(inputFile.getParentFile());
        }
    }

    /**
     * Provides an implementation of the <code>readProductNodes</code> interface method. Clients implementing this
     * method can be sure that the input object and eventually the subset information has already been set.
     * <p/>
     * <p>This method is called as a last step in the <code>readProductNodes(input, subsetInfo)</code> method.
     *
     * @throws java.io.IOException if an I/O error occurs
     */
    @Override
    protected Product readProductNodesImpl() throws IOException {
        final File inputFile = ReaderUtils.getFileFromInput(getInput());

        productDir = createProductDir(inputFile);

        Product product = null;
        try {
            dataDir = createProductDirectory(productDir);
            dataDir.readProductDirectory();
            product = dataDir.createProduct();
            product.setFileLocation(inputFile);
            setQuicklookBandName(product);
            product.getGcpGroup();
            product.setProductReader(this);
            product.setModified(false);
        } catch (Exception e) {
            handleReaderException(e);
        }

        return product;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void readBandRasterDataImpl(int sourceOffsetX, int sourceOffsetY, int sourceWidth, int sourceHeight,
                                          int sourceStepX, int sourceStepY, Band destBand, int destOffsetX,
                                          int destOffsetY, int destWidth, int destHeight, ProductData destBuffer,
                                          ProgressMonitor pm) throws IOException {
        try {
            final CEOSImageFile imageFile = dataDir.getImageFile(destBand);
            final int bitsPerSample = imageFile.getBitsPerSample();
            if (bitsPerSample == 8) {
                if (dataDir.isSLC()) {
                    boolean oneOf2 = destBand.getUnit().equals(Unit.REAL) || !destBand.getName().startsWith("q");

                    imageFile.readBandRasterDataSLCByte(sourceOffsetX, sourceOffsetY,
                            sourceWidth, sourceHeight,
                            sourceStepX, sourceStepY,
                            destWidth,
                            destBuffer, oneOf2, pm);

                } else {
                    imageFile.readBandRasterDataByte(sourceOffsetX, sourceOffsetY,
                            sourceWidth, sourceHeight,
                            sourceStepX, sourceStepY,
                            destWidth,
                            destBuffer, pm);
                }
            } else {
                if (dataDir.isSLC()) {
                    final boolean oneOf2 = destBand.getUnit().equals(Unit.REAL) || !destBand.getName().startsWith("q");
                    final int samplesPerGroup = imageFile.getSamplesPerDataGroup();
                    final int elemSize = (samplesPerGroup * ProductData.getElemSize(destBuffer.getType()));

                    imageFile.readBandRasterDataSLC(sourceOffsetX, sourceOffsetY,
                            sourceWidth, sourceHeight,
                            sourceStepX, sourceStepY,
                            destWidth,
                            destBuffer, oneOf2, elemSize);

                } else {
                    imageFile.readBandRasterDataShort(sourceOffsetX, sourceOffsetY,
                            sourceWidth, sourceHeight,
                            sourceStepX, sourceStepY,
                            destWidth,
                            destBuffer, pm);
                }
            }

        } catch (Exception e) {
            //final IOException ioException = new IOException(e.getMessage());
            //ioException.initCause(e);
            //throw ioException;
        }
    }
}
