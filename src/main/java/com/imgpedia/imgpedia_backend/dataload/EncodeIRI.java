package com.imgpedia.imgpedia_backend.dataload;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.system.StreamRDFBase;

/**
 * A class that encodes IRIs in a Jena Model by replacing certain characters with their percent-encoded equivalents.
 */
public class EncodeIRI extends StreamRDFBase {
    private final Model model;

    public EncodeIRI(Model model) {
        this.model = model;
    }

    @Override
    public void triple(Triple triple) {
        Node subject = normalizeNode(triple.getSubject());
        Node predicate = normalizeNode(triple.getPredicate());
        Node object = normalizeNode(triple.getObject());

        model.getGraph().add(Triple.create(subject, predicate, object));
    }

    private Node normalizeNode(Node node) {
        if (node.isURI()) {
            String normalizedUri = normalizeUri(node.getURI());
            if (!node.getURI().equals(normalizedUri)) {
                return NodeFactory.createURI(normalizedUri);
            }
        }
        return node;
    }

    /**This method use percent encoding to fix 
     * the issue with special characters in URIs.
     */
    private String normalizeUri(String uri) {
        return uri.replace("\"", "%22")
                  .replace("[", "%5B")
                  .replace("]", "%5D")
                  .replace(" ", "%20")
                  .replace("<", "%3C")
                  .replace(">", "%3E")
                  .replace("{", "%7B")
                  .replace("}", "%7D")
                  .replace("|", "%7C")
                  .replace("\\", "%5C")
                  .replace("^", "%5E")
                  .replace("`", "%60")
                  .replace("'", "%27")
                  .replace("\n", "%0A")
                  .replace("\r", "%0D")
                  .replace("\t", "%09");
    }

}