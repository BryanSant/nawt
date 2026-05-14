import cc.nawt.backend.macos.MacosPeerFactory;
import cc.nawt.spi.PeerFactory;

module cc.nawt.backend.macos {
    requires cc.nawt.api;

    provides PeerFactory with MacosPeerFactory;
}
