package cc.nawt.spi;

public non-sealed interface NavigationSplitPeer extends Peer {
    void setSidebar(Peer sidebar);
    void setDetail(Peer detail);
}
