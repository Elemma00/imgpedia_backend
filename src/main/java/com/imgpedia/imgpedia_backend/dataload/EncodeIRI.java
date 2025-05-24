package com.imgpedia.imgpedia_backend.dataload;

import java.nio.charset.StandardCharsets;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.system.StreamRDFBase;

/**
 * A class that encodes IRIs in a Jena Model by replacing illegal characters with their percent-encoded equivalents.
 * Ensures compatibility with TDB2 Loader and avoids warnings/errors.
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

        // Opcional: solo agrega el triple si las IRIs no están vacías
        if (isValidIriNode(subject) && isValidIriNode(predicate) && isValidIriNode(object)) {
            model.getGraph().add(Triple.create(subject, predicate, object));
        }
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

    /**
     * Percent-encodes all characters not allowed in RFC 3986 IRIs.
     * Encodes: space, <, >, ", {, }, |, \, ^, `, control chars, and non-ASCII as needed.
     */
    private String normalizeUri(String uri) {
        StringBuilder sb = new StringBuilder();
        for (char c : uri.toCharArray()) {
            if (isAllowedInUri(c)) {
                sb.append(c);
            } else {
                byte[] bytes = String.valueOf(c).getBytes(StandardCharsets.UTF_8);
                for (byte b : bytes) {
                    sb.append(String.format("%%%02X", b));
                }
            }
        }
        return sb.toString();
    }

    /**
     * Returns true if the character is allowed in a URI (unreserved or reserved per RFC 3986).
     * Prohibits space, <, >, ", {, }, |, \, ^, `, and control chars.
     */
    private boolean isAllowedInUri(char c) {
        // Prohibidos explícitamente por RFC 3987 y Turtle: space, <, >, ", {, }, |, \, ^, `
        if (c == ' ' || c == '<' || c == '>' || c == '"' ||
            c == '{' || c == '}' || c == '|' || c == '\\' ||
            c == '^' || c == '`' || c == '\n' || c == '\r' ||
            c < 0x21 || c > 0x7E) {
            return false;
        }
        // Unreserved: ALPHA / DIGIT / "-" / "." / "_" / "~"
        // Reserved:   ":" / "/" / "?" / "#" / "[" / "]" / "@" / "!" / "$" / "&" / "'" / "(" / ")" / "*" / "+" / "," / ";" / "="
        return (c >= 'A' && c <= 'Z') ||
               (c >= 'a' && c <= 'z') ||
               (c >= '0' && c <= '9') ||
               c == '-' || c == '.' || c == '_' || c == '~' ||
               c == ':' || c == '/' || c == '?' || c == '#' || c == '[' || c == ']' ||
               c == '@' || c == '!' || c == '$' || c == '&' || c == '\'' || c == '(' ||
               c == ')' || c == '*' || c == '+' || c == ',' || c == ';' || c == '=';
    }

    // Opcional: chequea que la IRI no esté vacía ni termine en ':' (error común en ontology)
    private boolean isValidIriNode(Node node) {
        if (!node.isURI()) return true;
        String uri = node.getURI();
        return uri != null && !uri.trim().isEmpty() && !uri.endsWith(":");
    }
}