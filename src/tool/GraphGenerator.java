package tool;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;

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

import view.App;
import view.feature.Console;

public class GraphGenerator {
	
	private ArrayList<String[][]> matrixNodes;
	private ArrayList<String[][]> matrixEdges;
	
	private LinkedHashMap<Node, LinkedHashSet<Integer>> nodesInterval;
	private HashMap<Node, HashMap<Integer, String>> nodesClustersInterval;
	private LinkedHashMap<Edge, LinkedHashSet<Integer>> edgesInterval;
	
	private ProjectController pc;
	
	private GraphModel graphModel;
	
	public GraphGenerator(ArrayList<String[][]> matrixNodes, ArrayList<String[][]> matrixEdges) {
		this.matrixNodes = matrixNodes;
		this.matrixEdges = matrixEdges;
		
		this.nodesInterval = new LinkedHashMap<Node, LinkedHashSet<Integer>>();
		this.nodesClustersInterval = new HashMap<Node, HashMap<Integer, String>>();
		this.edgesInterval = new LinkedHashMap<Edge, LinkedHashSet<Integer>>();
		
		init();
	}
	
	private void init() {
		pc = Lookup.getDefault().lookup(ProjectController.class);
        pc.newProject();
        
        pc.newWorkspace(pc.getCurrentProject());
        
        Lookup.getDefault().lookup(ContainerFactory.class).newContainer();
	}
	
	/**
	 * Generate Gephi graph
	 */
	private void generateGraph() {
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
					
					// Clusters label
					nodesClustersInterval.get(targetedNode).put(time, node[2]);
				} else {
					Node n = graphModel.factory().newNode(node[1]);
					
					n.getNodeData().getAttributes().setValue("Label", node[1]);
					
					graph.addNode(n);
					
					nodesKey.put(node[1], n);
					
					LinkedHashSet<Integer> interval = new LinkedHashSet<Integer>();
					HashMap<Integer, String> clusterInterval = new HashMap<Integer, String>();
					
					interval.add(time);
					clusterInterval.put(time, node[2]);
					
					nodesInterval.put(n, interval);
					nodesClustersInterval.put(n, clusterInterval);
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
	
	/**
	 * Generate all the intervals to fill dynamic columns
	 */
	private void createIntervals() {
		// Nodes interval
		for(Map.Entry<Node, LinkedHashSet<Integer>> entry : nodesInterval.entrySet()) {
			LinkedHashSet<Integer> intervals = entry.getValue();
			
			// Interval time of node life
			entry.getKey().getNodeData().getAttributes().setValue("time", createInterval(intervals));
			
			// Interval time of cluster label
			entry.getKey().getNodeData().getAttributes().setValue("cluster", createClustersInterval(entry.getKey()));
		}
		
		// Edges interval
		for(Map.Entry<Edge, LinkedHashSet<Integer>> entry : edgesInterval.entrySet()) {
			LinkedHashSet<Integer> intervals = entry.getValue();
			
			entry.getKey().getEdgeData().getAttributes().setValue("time", createInterval(intervals));
		}
	}
	
	/**
	 * Create intervals as string, for instance : [1, 3] = fromtime 1 totime 3
	 * @param intervals
	 * @return
	 */
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
	
	/**
	 * Create interval time about cluster 
	 * @param n node targeted
	 * @return
	 */
	private String createClustersInterval(Node n) {
		String strInterval = "";
		String currentLabel = "";

		boolean initInterval = false;
		int time = 0;
		
		// [firstTime, time]
		int firstTime = 0;
		
		int lastTime = 0;
		
		HashMap<Integer, String> labelInteval = nodesClustersInterval.get(n);
		
		for(Entry<Integer, String> entry : labelInteval.entrySet()) {
			time = entry.getKey();
			
			if(!initInterval) {
				// Allows to logically add the time interval, for instance : [1, 2)[2, 3]
				if(lastTime > 0) {
					firstTime = lastTime;
				} else {
					firstTime = time;
				}
				
				strInterval += " [" + firstTime + ", ";
				
				currentLabel = entry.getValue();
				initInterval = true;
			}
			
			if(labelInteval.containsKey(time + 1)) {
				// If the next label is different from the last, close the current interval and start a new one
				if(!labelInteval.get(time + 1).equals(currentLabel)) {
					// Case where fromtime = 1 and totime = 1, Gephi doesn't permit this, must increment t before 
					// in order to make [1, 2] instead of [1, 1]
					if(firstTime == time) {
						time += 1;
					}
					strInterval += time + ", " + currentLabel + "];";
					
					firstTime = 0;
					lastTime = time;
					
					initInterval = false;
				}
			} else { // Close the last interval when it's the end of the map
				strInterval += "Infinity, " + entry.getValue() + "]";
			}
		}
		
		return strInterval;
	}
	
	/**
	 * Export the graph in gexf format
	 */
	public void renderGraph() {
        AttributeModel attModel = Lookup.getDefault().lookup(AttributeController.class).getModel();
        AttributeColumnsController timeColumn = Lookup.getDefault().lookup(AttributeColumnsController.class);
        AttributeColumnsController clusterColumn = Lookup.getDefault().lookup(AttributeColumnsController.class);
        
        Lookup.getDefault().lookup(DynamicController.class).getModel();
        graphModel = Lookup.getDefault().lookup(GraphController.class).getModel();
        
        generateGraph();
        
        // Time interval for edges and nodes
        timeColumn.addAttributeColumn(attModel.getNodeTable(), "time", AttributeType.TIME_INTERVAL);
        timeColumn.addAttributeColumn(attModel.getEdgeTable(), "time", AttributeType.TIME_INTERVAL);
        
        // Cluster attributes linked to nodes
        clusterColumn.addAttributeColumn(attModel.getNodeTable(), "cluster", AttributeType.DYNAMIC_STRING);
        
        timeColumn.convertAttributeColumnToDynamic(attModel.getEdgeTable(), attModel.getEdgeTable().getColumn("Weight"), 
        		2000, Double.POSITIVE_INFINITY, false, false);
        
        createIntervals();
        
        // Necessary
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ex) {
            ex.printStackTrace();
            System.exit(1);
        }
        
        ExportController ec = Lookup.getDefault().lookup(ExportController.class);
        try {
        	File file = new File(App.DATA_PATH + "\\" + App.TABLE_NAME + ".gexf");
        	ec.exportFile(file);
        	
        	Console.getConsole().logConsole("File " + file.getAbsolutePath() + " created!\n\n", Console.SUCCESS);
        } catch(IOException ex) {
        	ex.printStackTrace();
        }
	}
}
