package core;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;

import org.gephi.data.attributes.api.AttributeController;
import org.gephi.data.attributes.api.AttributeModel;
import org.gephi.data.attributes.api.AttributeType;
import org.gephi.datalab.api.AttributeColumnsController;
import org.gephi.dynamic.api.DynamicController;
import org.gephi.graph.api.Edge;
import org.gephi.graph.api.GraphController;
import org.gephi.graph.api.GraphModel;
import org.gephi.graph.api.Node;
import org.gephi.graph.api.UndirectedGraph;
import org.gephi.io.exporter.api.ExportController;
import org.gephi.io.importer.api.ContainerFactory;
import org.gephi.project.api.ProjectController;
import org.openide.util.Lookup;

public class GraphGenerator {
	
	private ArrayList<String[][]> matrixNodes;
	private ArrayList<String[][]> matrixEdges;
	
	private HashMap<Node, LinkedHashSet<Integer>> nodesInterval;
	private HashMap<Edge, LinkedHashSet<Integer>> edgesInterval;
	
	private ProjectController pc;
	
	private GraphModel graphModel;
	
	public GraphGenerator(ArrayList<String[][]> matrixNodes, ArrayList<String[][]> matrixEdges) {
		this.matrixNodes = matrixNodes;
		this.matrixEdges = matrixEdges;
		
		this.nodesInterval = new HashMap<Node, LinkedHashSet<Integer>>();
		this.edgesInterval = new HashMap<Edge, LinkedHashSet<Integer>>();
		
		init();
	}
	
	private void init() {
		pc = Lookup.getDefault().lookup(ProjectController.class);
        pc.newProject();
        
        pc.newWorkspace(pc.getCurrentProject());
        
        Lookup.getDefault().lookup(ContainerFactory.class).newContainer();
	}
	
	private void genGraph() {
		HashMap<String, Node> nodesKey = new HashMap<String, Node>();
		
		UndirectedGraph graph = graphModel.getUndirectedGraph();
		
		int time = 0;
		for(String[][] nodes : matrixNodes) {
			time += 1;
			for(String[] node : nodes) {
				// If node has been created, add just the interval time
				if(nodesKey.containsKey(node[1])) {
					Node targetedNode = nodesKey.get(node[1]);
					nodesInterval.get(targetedNode).add(time);
				} else {
					Node n = graphModel.factory().newNode(node[1]);
					
					n.getNodeData().getAttributes().setValue("Label", node[1]);
					n.getNodeData().getAttributes().setValue("cluster", node[2]);
					
					graph.addNode(n);
					
					nodesKey.put(node[1], n);
					
					LinkedHashSet<Integer> interval = new LinkedHashSet<Integer>();
					interval.add(time);
					nodesInterval.put(n, interval);
				}
			}
		}
		
		time = 0;
		for(String[][] edges : matrixEdges) {
			time += 1; 
			for(String[] edge : edges) {
				Node node1 = nodesKey.get(edge[0]);
				Node node2 = nodesKey.get(edge[1]);
				
				Edge targetedEdge = graphModel.getUndirectedGraph().getEdge(node1, node2);
				
				// If edge has been created, add just interval time
				if(targetedEdge != null) {
					edgesInterval.get(targetedEdge).add(time);
				} else {
					Edge e = graphModel.factory().newEdge(node1, node2, 1f, false);
					
					LinkedHashSet<Integer> interval = new LinkedHashSet<Integer>();
					interval.add(time);
					edgesInterval.put(e, interval);
					
					graph.addEdge(e);
				}
			}
		}
	}
	
	private void genIntervals() {
		// Nodes interval
		for(Map.Entry<Node, LinkedHashSet<Integer>> entry : nodesInterval.entrySet()) {
			LinkedHashSet<Integer> intervals = entry.getValue();
			
			entry.getKey().getNodeData().getAttributes().setValue("time", createInterval(intervals));
		}
		
		// Edges interval
		for(Map.Entry<Edge, LinkedHashSet<Integer>> entry : edgesInterval.entrySet()) {
			LinkedHashSet<Integer> intervals = entry.getValue();
			
			entry.getKey().getEdgeData().getAttributes().setValue("time", createInterval(intervals));
		}
	}
	
	private String createInterval(LinkedHashSet<Integer> intervals) {
		String strInterval = "";
		boolean initInterval = false;
		for(int time : intervals) {
			if(!intervals.contains(time + 1)) {
				strInterval += time + "];";
				initInterval = false;
			} else if(!initInterval) {
				strInterval += " [" + time + ", ";
				initInterval = true;
			}
		}
		
		return strInterval;
	}
	
	public void renderGraph() {
        AttributeModel attModel = Lookup.getDefault().lookup(AttributeController.class).getModel();
        AttributeColumnsController timeColumn = Lookup.getDefault().lookup(AttributeColumnsController.class);
        AttributeColumnsController clusterColumn = Lookup.getDefault().lookup(AttributeColumnsController.class);
        
        Lookup.getDefault().lookup(DynamicController.class).getModel();
        graphModel = Lookup.getDefault().lookup(GraphController.class).getModel();
        
        genGraph();
        
        timeColumn.addAttributeColumn(attModel.getNodeTable(), "time", AttributeType.TIME_INTERVAL);
        timeColumn.addAttributeColumn(attModel.getEdgeTable(), "time", AttributeType.TIME_INTERVAL);
        
        clusterColumn.addAttributeColumn(attModel.getNodeTable(), "cluster", AttributeType.STRING);
        
        timeColumn.convertAttributeColumnToDynamic(attModel.getEdgeTable(), attModel.getEdgeTable().getColumn("Weight"), 
        		2000, Double.POSITIVE_INFINITY, false, false);
        
        genIntervals();
        
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ex) {
            ex.printStackTrace();
            System.exit(1);
        }
        
        ExportController ec = Lookup.getDefault().lookup(ExportController.class);
        try {
        	File file = new File(App.DATA_PATH + "\\" + App.TABLE_NAME + ".gexf\n\n");
        	ec.exportFile(file);
        	
        	App.logConsole("File " + file.getAbsolutePath() + " created!", App.SUCCESS);
        } catch(IOException ex) {
        	ex.printStackTrace();
        }
	}
}