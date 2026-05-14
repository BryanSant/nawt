package cc.nawt.spi;

import java.util.ArrayList;
import java.util.List;

/** Helper for backend tree peers that render trees as a flat indented list. */
public final class TreeFlattener {
    private TreeFlattener() {}

    public record Row(int[] path, int depth, String label) {}

    /** Flatten {@code root} depth-first; the root itself is row 0 unless {@code includeRoot} is false. */
    public static List<Row> flatten(TreeNodeData root, boolean includeRoot) {
        List<Row> out = new ArrayList<>();
        if (root == null) return out;
        if (includeRoot) {
            out.add(new Row(new int[0], 0, root.label()));
        }
        addChildren(out, root, new int[0], includeRoot ? 1 : 0);
        return out;
    }

    private static void addChildren(List<Row> out, TreeNodeData parent, int[] parentPath, int depth) {
        List<TreeNodeData> kids = parent.children();
        for (int i = 0; i < kids.size(); i++) {
            int[] path = new int[parentPath.length + 1];
            System.arraycopy(parentPath, 0, path, 0, parentPath.length);
            path[parentPath.length] = i;
            TreeNodeData child = kids.get(i);
            out.add(new Row(path, depth, child.label()));
            addChildren(out, child, path, depth + 1);
        }
    }

    /** Render a label with leading whitespace appropriate for {@code depth}. */
    public static String indented(int depth, String label) {
        if (depth == 0) return label;
        StringBuilder sb = new StringBuilder(depth * 2 + label.length());
        for (int i = 0; i < depth; i++) sb.append("  ");
        sb.append(label);
        return sb.toString();
    }
}
