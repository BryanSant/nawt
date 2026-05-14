package cc.nawt.spi;

import cc.nawt.Coordinate;

public non-sealed interface MapPeer extends Peer {
    void setCenter(Coordinate center);
    void setZoom(double zoom);
    void addPin(Coordinate at, String title);
    void clearPins();
    void setInteractive(boolean enabled);
}
