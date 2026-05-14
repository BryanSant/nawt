import cc.nawt.backend.gtk.GtkPeerFactory;
import cc.nawt.spi.PeerFactory;

module cc.nawt.backend.gtk {
    requires cc.nawt.api;

    provides PeerFactory with GtkPeerFactory;
}
