package org.esa.snap.product.library.v2.database;

import org.esa.snap.remote.products.repository.ThreadStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Created by jcoravu on 4/10/2019.
 */
public class AddLocalRepositoryFolderHelper extends AbstractLocalRepositoryFolderHelper {

    private static final Logger logger = Logger.getLogger(AddLocalRepositoryFolderHelper.class.getName());

    public AddLocalRepositoryFolderHelper(AllLocalFolderProductsRepository allLocalFolderProductsRepository) {
        super(allLocalFolderProductsRepository);
    }

    public List<SaveProductData> addValidProductsFromFolder(Path localRepositoryFolderPath, ThreadStatus threadStatus)
                                                            throws IOException, SQLException, InterruptedException {

        List<SaveProductData> savedProducts = null;
        if (Files.exists(localRepositoryFolderPath)) {
            // the local repository folder exists on the disk
            ThreadStatus.checkCancelled(threadStatus);

            List<LocalProductMetadata> existingLocalRepositoryProducts = this.allLocalFolderProductsRepository.loadRepositoryProductsMetadata(localRepositoryFolderPath);

            ThreadStatus.checkCancelled(threadStatus);

            savedProducts = saveProductsFromFolder(localRepositoryFolderPath, existingLocalRepositoryProducts, true, threadStatus);
        } else {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "The local repository folder '"+localRepositoryFolderPath.toString()+"' does not exist.");
            }
        }
        return savedProducts;
    }

    private List<SaveProductData> saveProductsFromFolder(Path localRepositoryFolderPath, List<LocalProductMetadata> existingLocalRepositoryProducts,
                                                           boolean scanOnlyFirstLevel, ThreadStatus threadStatus)
                                                           throws IOException, InterruptedException {

        List<SaveProductData> savedProducts = new ArrayList<>();
        Stack<Path> stack = new Stack<>();
        stack.push(localRepositoryFolderPath);
        while (!stack.isEmpty()) {
            Path currentPath = stack.pop();

            ThreadStatus.checkCancelled(threadStatus);

            if (Files.isDirectory(currentPath)) {
                // the current path is a folder
                try (Stream<Path> stream = Files.list(currentPath)) {

                    ThreadStatus.checkCancelled(threadStatus);

                    Iterator<Path> it = stream.iterator();
                    while (it.hasNext()) {

                        ThreadStatus.checkCancelled(threadStatus);

                        Path productPath = it.next();
                        try {
                            LocalProductMetadata localProductMetadata = foundLocalProductMetadata(localRepositoryFolderPath, existingLocalRepositoryProducts, productPath);
                            SaveProductData saveProductData = null;
                            if (localProductMetadata != null) {
                                // the product already exists into the database
                                FileTime fileTime = Files.getLastModifiedTime(productPath);
                                if (fileTime.toMillis() == localProductMetadata.getLastModifiedDate().getTime()) {
                                    // unchanged product
                                    saveProductData = new SaveProductData(localProductMetadata.getId(), null, null, null);
                                    savedProducts.add(saveProductData);
                                }
                            }

                            ThreadStatus.checkCancelled(threadStatus);

                            if (saveProductData == null) {
                                // read and save the product into the database
                                saveProductData = readAndSaveProduct(localRepositoryFolderPath, productPath, threadStatus);
                                if (saveProductData == null) {
                                    // no product has been loaded from the path
                                    if (!scanOnlyFirstLevel) {
                                        if (Files.isDirectory(productPath)) {
                                            stack.push(productPath);
                                        }
                                    }
                                } else {
                                    // the product has been saved into the database
                                    savedProducts.add(saveProductData);
                                    finishSavingProduct(saveProductData);
                                }
                            } else {
                                // unchanged product
                                if (logger.isLoggable(Level.FINE)) {
                                    logger.log(Level.FINE, "The local product from the path '"+productPath.toString()+"' is unchanged.");
                                }
                            }
                        } catch (Exception exception) {
                            logger.log(Level.SEVERE, "Failed to save the local product from the path '" + productPath.toString() + "'.", exception);
                        }
                    }
                }
            } else {
                // the current path is not a folder
                throw new IllegalStateException("The path '"+currentPath.toString()+"' is not a folder.");
            }
        }
        return savedProducts;
    }

    private static LocalProductMetadata foundLocalProductMetadata(Path localRepositoryFolderPath, List<LocalProductMetadata> existingLocalRepositoryProducts, Path productPathToCheck) {
        for (int i = 0; i<existingLocalRepositoryProducts.size(); i++) {
            LocalProductMetadata localProductMetadata = existingLocalRepositoryProducts.get(i);
            Path path = localRepositoryFolderPath.resolve(localProductMetadata.getRelativePath());
            if (path.compareTo(productPathToCheck) == 0) {
                return localProductMetadata; // the same product path
            }
        }
        return null;
    }
}
