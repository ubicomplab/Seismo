package ubicomp.uw.edu.seismo;

import android.media.Image;
import android.media.ImageReader;

import java.nio.ByteBuffer;

public class PPGExtractor {

    public double[] extractPPG(ImageReader reader) {

        Image image = reader.acquireNextImage();

        Image.Plane[] planes = image.getPlanes();
        double[] ppg = new double[planes.length];

        for (int chan = 0; chan < planes.length; chan++) {
            ByteBuffer buf = image.getPlanes()[chan].getBuffer();
            byte[] bytes = new byte[buf.capacity()];
            buf.get(bytes);

            ppg[chan] = 0;
            for (int i = 0; i < bytes.length; i++) {
                ppg[chan] += bytes[i];
            }
        }

        image.close();

        return ppg;
    }

}
