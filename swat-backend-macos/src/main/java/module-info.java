import io.github.swat.backend.macos.MacosPeerFactory;
import io.github.swat.spi.PeerFactory;

module io.github.swat.backend.macos {
    requires io.github.swat.api;

    provides PeerFactory with MacosPeerFactory;
}
