package com.framstag.llmaj.tools.java.graph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Builds the PlantUML source of the dependency diagrams of one module.
 *
 * <p>Two levels: a package overview that is always emitted, and class detail per group of the cheapest
 * separation. Everything is derived from the class reference graph, so the source is the same on every run and
 * no model-generated content reaches it. A class name that could break the diagram syntax stops the emission
 * and is reported instead of being stored as broken source.
 */
public final class DependencyDiagrams {
    /**
     * The budgets of an emitted diagram. Every one of them is reported in the caption of the diagram it
     * applies to: an omission that is not stated would make the picture lie about the module.
     *
     * <p>The overview has its own node budget, because the two levels do not scale the same way: a module of
     * 114 classes can live in 21 packages and still hold one group of 67 classes, so a bound that fits the
     * class level leaves the package level nothing to draw. Measured: jablib spans 147 packages.
     */
    public record DiagramSettings(int maxNodes,
                                  int maxOverviewNodes,
                                  int minEdgeWeight,
                                  int maxEdgesPerNode,
                                  int maxGroupDiagrams) {
        public static final DiagramSettings DEFAULT = new DiagramSettings(80, 250, 2, 8, 12);
    }

    public record Diagram(String name, String title, String caption, String source) {
    }

    public record DiagramSet(String moduleName,
                             Diagram overview,
                             List<Diagram> groups,
                             List<String> notDrawn,
                             String reasoning) {
    }

    /** A class name goes into a quoted PlantUML label and has to be harmless there. */
    private static final Pattern SAFE_LABEL = Pattern.compile("[A-Za-z0-9_.$]+");

    /** A group that couples to many other groups would otherwise fill the picture with collapsed nodes. */
    private static final int MAX_COLLAPSED_GROUPS = 8;

    private DependencyDiagrams() {
    }

    public static DiagramSet of(IntraModuleGraph graph, DiagramSettings settings) {
        MapWeightedGraph production = graph.productionReferenceGraph();
        Set<String> productionNodes = new LinkedHashSet<>(production.nodes());

        List<String> unsafeNames = productionNodes.stream()
                .filter(name -> !SAFE_LABEL.matcher(name).matches())
                .sorted()
                .toList();

        if (!unsafeNames.isEmpty()) {
            return new DiagramSet(graph.moduleName(), null, List.of(),
                    List.of("No diagram was generated: the class name(s) " + unsafeNames
                            + " cannot be written into PlantUML source without breaking it."),
                    "Diagram generation was stopped because a class name cannot be represented in the"
                            + " diagram syntax. No broken source was stored.");
        }

        List<String> notDrawn = new ArrayList<>();

        Diagram overview = packageOverview(graph, production, settings, notDrawn);
        List<Diagram> groups = groupDetail(graph, production, settings, notDrawn);

        return new DiagramSet(graph.moduleName(), overview, groups, notDrawn, reasoning(graph, overview, groups));
    }

    private static String reasoning(IntraModuleGraph graph, Diagram overview, List<Diagram> groups) {
        return "Diagrams for module '" + graph.moduleName() + "': "
                + (overview == null ? "no package overview" : "one package overview")
                + ", " + groups.size() + " class detail diagram(s). The source is derived from the class"
                + " reference graph, so it is identical on every run and carries no model-generated content."
                + " Omissions are stated per diagram.";
    }

    private static Diagram packageOverview(IntraModuleGraph graph,
                                           MapWeightedGraph production,
                                           DiagramSettings settings,
                                           List<String> notDrawn) {
        Map<String, Integer> classesByPackage = new TreeMap<>();
        for (String node : production.nodes()) {
            classesByPackage.merge(packageOf(node), 1, Integer::sum);
        }

        if (classesByPackage.isEmpty()) {
            notDrawn.add("No package overview: the module has no production class.");

            return null;
        }

        if (classesByPackage.size() > settings.maxOverviewNodes()) {
            notDrawn.add("No package overview: the module has " + classesByPackage.size()
                    + " packages, above the node budget of " + settings.maxOverviewNodes() + ".");

            return null;
        }

        MapWeightedGraph packageGraph = new MapWeightedGraph();
        for (String pck : classesByPackage.keySet()) {
            packageGraph.addNode(pck);
        }

        for (GraphEdge edge : production.edges()) {
            String from = packageOf(edge.from());
            String to = packageOf(edge.to());

            if (!from.equals(to)) {
                packageGraph.addEdge(from, to, packageGraph.weight(from, to) + edge.weight());
            }
        }

        Set<String> productionNodes = new LinkedHashSet<>(production.nodes());
        int omittedEdges = 0;

        StringBuilder source = new StringBuilder();
        source.append("@startuml\n");
        source.append("title ").append(graph.moduleName()).append(" - packages\n");

        Map<String, String> aliasByPackage = new LinkedHashMap<>();
        int aliasIndex = 0;

        for (String pck : classesByPackage.keySet()) {
            String alias = "P" + aliasIndex++;
            aliasByPackage.put(pck, alias);
            source.append("package \"").append(packageLabel(pck)).append("\" as ").append(alias).append("\n");
        }

        for (GraphEdge edge : packageGraph.edges()) {
            if (edge.weight() < settings.minEdgeWeight()) {
                omittedEdges++;
                continue;
            }

            source.append(aliasByPackage.get(edge.from()))
                    .append(" --> ")
                    .append(aliasByPackage.get(edge.to()))
                    .append(" : ")
                    .append(edge.weight())
                    .append("\n");
        }

        source.append("@enduml\n");

        String caption = classesByPackage.size() + " package(s) covering " + productionNodes.size()
                + " production class(es), edges below weight " + settings.minEdgeWeight()
                + " omitted (" + omittedEdges + " omitted).";

        return diagram("overview", graph.moduleName() + " - packages", caption, source.toString(),
                "the package overview could not be written as valid PlantUML source", notDrawn);
    }

    private static List<Diagram> groupDetail(IntraModuleGraph graph,
                                             MapWeightedGraph production,
                                             DiagramSettings settings,
                                             List<String> notDrawn) {
        List<Set<String>> groups = levelTwoGroups(graph, production);

        List<Set<String>> groupsBySize = groups.stream()
                .sorted(Comparator.comparingInt((Set<String> group) -> group.size()).reversed()
                        .thenComparing(group -> group.iterator().next()))
                .toList();

        List<Diagram> diagrams = new ArrayList<>();
        int drawn = 0;

        for (int index = 0; index < groupsBySize.size(); index++) {
            Set<String> group = groupsBySize.get(index);
            String label = "group " + (index + 1) + " of " + groupsBySize.size() + " (" + group.size()
                    + " class(es), " + dominantPackageOf(group) + ")";

            if (group.size() < 2) {
                // A diagram of one class shows no structure at all. It is a group of the separation, but there
                // is nothing to draw in it, and filling the diagram budget with such pictures would push out
                // the groups that do have structure.
                continue;
            }

            if (group.size() > settings.maxNodes()) {
                notDrawn.add("No class detail for " + label + ": above the node budget of "
                        + settings.maxNodes() + ".");
                continue;
            }

            if (drawn >= settings.maxGroupDiagrams()) {
                notDrawn.add("No class detail for " + label + ": the budget of "
                        + settings.maxGroupDiagrams() + " class detail diagrams is used up.");
                continue;
            }

            Diagram diagram = groupDiagram(graph, production, group, groupsBySize, label, settings, notDrawn);

            if (diagram != null) {
                diagrams.add(diagram);
                drawn++;
            }
        }

        if (groupsBySize.isEmpty()) {
            notDrawn.add("No class detail: the module has no production class.");
        } else if (diagrams.isEmpty()) {
            notDrawn.add("No class detail diagram was drawn: every group of the cheapest separation either"
                    + " holds a single class or is above the node budget of " + settings.maxNodes() + ".");
        }

        return diagrams;
    }

    /**
     * The groups the class detail is drawn for are the groups of the cheapest separation, which is the split
     * at the lowest price. A module whose production classes are already separate has no separation to draw,
     * so its packages are drawn instead, which keeps the second level available.
     */
    private static List<Set<String>> levelTwoGroups(IntraModuleGraph graph, MapWeightedGraph production) {
        List<SeparationLadder.Separation> ladder = SeparationLadder.of(production);

        if (!ladder.isEmpty()) {
            return ladder.getFirst().groups();
        }

        Map<String, Set<String>> groupsByPackage = new TreeMap<>();

        for (String node : production.nodes()) {
            groupsByPackage.computeIfAbsent(packageOf(node), ignored -> new TreeSet<>()).add(node);
        }

        return List.copyOf(groupsByPackage.values());
    }

    private static Diagram groupDiagram(IntraModuleGraph graph,
                                        MapWeightedGraph production,
                                        Set<String> group,
                                        List<Set<String>> allGroups,
                                        String label,
                                        DiagramSettings settings,
                                        List<String> notDrawn) {
        Map<String, Integer> groupIndexOfNode = new LinkedHashMap<>();
        for (int index = 0; index < allGroups.size(); index++) {
            for (String member : allGroups.get(index)) {
                groupIndexOfNode.put(member, index);
            }
        }

        int ownIndex = groupIndexOfNode.get(group.iterator().next());

        Map<String, Integer> weightsToOtherGroups = new TreeMap<>();
        Map<String, String> heaviestMemberToOtherGroup = new TreeMap<>();
        Map<String, Integer> heaviestWeightToOtherGroup = new TreeMap<>();
        List<GraphEdge> internalEdges = new ArrayList<>();
        List<GraphEdge> structuralEdges = new ArrayList<>();
        Set<String> internallyConnected = new TreeSet<>();
        int omittedEdges = 0;

        for (GraphEdge edge : production.edges()) {
            if (group.contains(edge.from()) && group.contains(edge.to())) {
                internallyConnected.add(edge.from() + "\u0000" + edge.to());

                if (edge.weight() < settings.minEdgeWeight()) {
                    omittedEdges++;
                } else {
                    internalEdges.add(edge);
                }
                continue;
            }

            if (!group.contains(edge.from()) && !group.contains(edge.to())) {
                continue;
            }

            String member = group.contains(edge.from()) ? edge.from() : edge.to();
            String outside = group.contains(edge.from()) ? edge.to() : edge.from();
            String groupKey = "G" + groupIndexOfNode.get(outside);

            weightsToOtherGroups.merge(groupKey, edge.weight(), Integer::sum);

            if (edge.weight() > heaviestWeightToOtherGroup.getOrDefault(groupKey, 0)) {
                heaviestWeightToOtherGroup.put(groupKey, edge.weight());
                heaviestMemberToOtherGroup.put(groupKey, member);
            }
        }

        // A structural relation is drawn whenever both of its endpoints are in the diagram, regardless of
        // any cutoff. Where the same pair is already drawn with a weight, the hierarchy is not drawn twice.
        for (IntraModuleGraph.StructuralLink link : graph.structuralLinks()) {
            if (!group.contains(link.from()) || !group.contains(link.to())) {
                continue;
            }

            String canonical = link.from().compareTo(link.to()) < 0
                    ? link.from() + "\u0000" + link.to()
                    : link.to() + "\u0000" + link.from();

            if (!internallyConnected.contains(canonical)) {
                structuralEdges.add(new GraphEdge(link.from(), link.to(), 1));
            }
        }

        List<GraphEdge> limitedInternalEdges = limitEdgesPerNode(internalEdges, settings.maxEdgesPerNode());
        omittedEdges += internalEdges.size() - limitedInternalEdges.size();

        int collapsedGroups = weightsToOtherGroups.size();

        if (collapsedGroups > MAX_COLLAPSED_GROUPS) {
            List<String> heaviest = weightsToOtherGroups.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(MAX_COLLAPSED_GROUPS)
                    .map(Map.Entry::getKey)
                    .toList();

            weightsToOtherGroups.keySet().retainAll(heaviest);
            heaviestMemberToOtherGroup.keySet().retainAll(heaviest);
        }

        StringBuilder source = new StringBuilder();
        source.append("@startuml\n");
        source.append("title ").append(graph.moduleName()).append(" - ").append(label).append("\n");

        Map<String, String> aliasByClass = new LinkedHashMap<>();
        int aliasIndex = 0;

        for (String member : new TreeSet<>(group)) {
            String alias = "C" + aliasIndex++;
            aliasByClass.put(member, alias);
            source.append("class \"").append(member).append("\" as ").append(alias).append("\n");
        }

        Map<String, String> aliasByGroup = new LinkedHashMap<>();
        int groupAliasIndex = 0;

        for (String groupKey : weightsToOtherGroups.keySet()) {
            int otherIndex = Integer.parseInt(groupKey.substring(1));
            Set<String> otherGroup = allGroups.get(otherIndex);
            String alias = "G" + groupAliasIndex++;
            aliasByGroup.put(groupKey, alias);
            source.append("rectangle \"group ").append(otherIndex + 1).append(" of ").append(allGroups.size())
                    .append(" (").append(otherGroup.size()).append(" class(es), ")
                    .append(dominantPackageOf(otherGroup)).append(")\" as ").append(alias).append("\n");
        }

        for (GraphEdge edge : limitedInternalEdges) {
            source.append(aliasByClass.get(edge.from()))
                    .append(" --> ")
                    .append(aliasByClass.get(edge.to()))
                    .append(" : ")
                    .append(edge.weight())
                    .append("\n");
        }

        for (GraphEdge edge : structuralEdges) {
            source.append(aliasByClass.get(edge.from()))
                    .append(" ..> ")
                    .append(aliasByClass.get(edge.to()))
                    .append(" : extends/implements\n");
        }

        for (Map.Entry<String, Integer> entry : weightsToOtherGroups.entrySet()) {
            source.append(aliasByClass.get(heaviestMemberToOtherGroup.get(entry.getKey())))
                    .append(" --> ")
                    .append(aliasByGroup.get(entry.getKey()))
                    .append(" : ")
                    .append(entry.getValue())
                    .append("\n");
        }

        source.append("@enduml\n");

        String caption = group.size() + " of " + group.size() + " class(es) of " + label
                + ", edges below weight " + settings.minEdgeWeight() + " omitted (" + omittedEdges
                + " omitted, at most " + settings.maxEdgesPerNode() + " edges per class), "
                + structuralEdges.size() + " structural relation(s) always drawn. "
                + (weightsToOtherGroups.isEmpty() ? "No coupling to another group."
                : weightsToOtherGroups.size()
                + " other group(s) are drawn collapsed, weighted with the total coupling to them"
                + (collapsedGroups > MAX_COLLAPSED_GROUPS
                ? ", the other " + (collapsedGroups - MAX_COLLAPSED_GROUPS) + " omitted" : "")
                + ".");

        Diagram diagram = diagram("group-" + (ownIndex + 1), graph.moduleName() + " - " + label, caption,
                source.toString(),
                "the class detail could not be written as valid PlantUML source", notDrawn);

        if (diagram == null) {
            notDrawn.add("No class detail for " + label + ": the source could not be written.");
        }

        return diagram;
    }

    private static List<GraphEdge> limitEdgesPerNode(List<GraphEdge> edges, int maxEdgesPerNode) {
        Map<String, Integer> kept = new TreeMap<>();
        List<GraphEdge> limited = new ArrayList<>();

        for (GraphEdge edge : edges.stream()
                .sorted(Comparator.comparingInt(GraphEdge::weight).reversed().thenComparing(edge -> edge))
                .toList()) {
            if (kept.getOrDefault(edge.from(), 0) >= maxEdgesPerNode
                    || kept.getOrDefault(edge.to(), 0) >= maxEdgesPerNode) {
                continue;
            }

            kept.merge(edge.from(), 1, Integer::sum);
            kept.merge(edge.to(), 1, Integer::sum);
            limited.add(edge);
        }

        limited.sort(Comparator.naturalOrder());

        return limited;
    }

    /**
     * Checks the generated source before it is stored. Structural, not a full parse: the source has to be
     * opened and closed, it has to carry at least one node, and every quoted label has to be closed on its own
     * line. A diagram that is silently broken is worse than no diagram.
     */
    public static boolean isValid(String source) {
        if (source == null) {
            return false;
        }

        String trimmed = source.trim();

        if (!trimmed.startsWith("@startuml") || !trimmed.endsWith("@enduml")) {
            return false;
        }

        boolean hasNode = false;

        for (String line : trimmed.split("\n", -1)) {
            if (line.chars().filter(character -> character == '"').count() % 2 != 0) {
                return false;
            }

            if (line.startsWith("class ") || line.startsWith("package ") || line.startsWith("rectangle ")) {
                hasNode = true;
            }
        }

        return hasNode;
    }

    private static Diagram diagram(String name,
                                   String title,
                                   String caption,
                                   String source,
                                   String failureReason,
                                   List<String> notDrawn) {
        if (!isValid(source)) {
            notDrawn.add("No diagram '" + name + "': " + failureReason + ".");

            return null;
        }

        return new Diagram(name, title, caption, source);
    }

    private static String dominantPackageOf(Set<String> group) {
        Map<String, Integer> membersByPackage = new TreeMap<>();

        for (String member : group) {
            membersByPackage.merge(packageOf(member), 1, Integer::sum);
        }

        String dominantPackage = "";
        int dominantPackageMemberCount = 0;

        for (Map.Entry<String, Integer> entry : membersByPackage.entrySet()) {
            if (entry.getValue() > dominantPackageMemberCount) {
                dominantPackage = entry.getKey();
                dominantPackageMemberCount = entry.getValue();
            }
        }

        int nonConforming = group.size() - dominantPackageMemberCount;

        return "mostly " + packageLabel(dominantPackage)
                + (nonConforming == 0 ? "" : ", " + nonConforming + " outside it");
    }

    private static String packageLabel(String packageName) {
        return packageName.isEmpty() ? "(default package)" : packageName;
    }

    private static String packageOf(String className) {
        int lastDot = className.lastIndexOf('.');

        return lastDot < 0 ? "" : className.substring(0, lastDot);
    }
}
