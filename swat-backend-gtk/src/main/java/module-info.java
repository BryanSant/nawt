import io.github.swat.backend.gtk.GtkPeerFactory;
import io.github.swat.spi.PeerFactory;

module io.github.swat.backend.gtk {
    requires io.github.swat.api;

    provides PeerFactory with GtkPeerFactory;
}
