import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.File;

public class PureRun {
    public static void main(String[] args) throws Exception {
        PDDocument doc = Loader.loadPDF(new File("before.pdf"));
        doc.save(new File("after.pdf"));
    }
}
