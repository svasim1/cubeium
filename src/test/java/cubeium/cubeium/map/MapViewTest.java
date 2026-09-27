package cubeium.cubeium.map;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MapViewTest {
    @Test
    void zoomKeepsThePointUnderTheCursorFixed() {
        MapView view = new MapView();
        view.center(1000, -500);
        view.setZoom(2);
        double dx = 150, dz = -80; // cursor offset from the map center in GUI pixels
        double beforeX = view.centerX() + dx * view.blocksPerPixel();
        double beforeZ = view.centerZ() + dz * view.blocksPerPixel();

        view.zoomAround(-3, dx, dz);

        assertEquals(beforeX, view.centerX() + dx * view.blocksPerPixel(), 1e-9);
        assertEquals(beforeZ, view.centerZ() + dz * view.blocksPerPixel(), 1e-9);
    }

    @Test
    void zoomIsClampedToPowersOfTwo() {
        MapView view = new MapView();
        view.setZoom(100);
        assertEquals(MapView.MAX_ZOOM, view.zoom());
        assertEquals(256, view.blocksPerPixel());
        view.setZoom(-100);
        assertEquals(0.25, view.blocksPerPixel());
    }

    @Test
    void draggingMovesTheWorldTheOtherWay() {
        MapView view = new MapView();
        view.setZoom(3); // 8 blocks per pixel
        view.panPixels(10, -5);
        assertEquals(-80, view.centerX());
        assertEquals(40, view.centerZ());
    }
}
