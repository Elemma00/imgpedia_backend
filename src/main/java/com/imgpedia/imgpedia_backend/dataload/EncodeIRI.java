package com.imgpedia.imgpedia_backend.dataload;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Node_Literal;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.system.StreamRDFBase;

/**
 * StreamRDF implementation that normalizes and filters RDF triples,
 * ensuring IRIs and literals are safe for serialization and conform to encoding rules.
 */
public class EncodeIRI extends StreamRDFBase {

    private final Model model;
    private int filteredTriples = 0;

    /**
     * Set of ontology prefixes that should never be modified.
     */
    private static final Set<String> ONTOLOGY_PREFIXES = new HashSet<>();
    static {
        ONTOLOGY_PREFIXES.add("http://www.w3.org/2002/07/owl#");
        ONTOLOGY_PREFIXES.add("http://www.w3.org/1999/02/22-rdf-syntax-ns#");
        ONTOLOGY_PREFIXES.add("http://www.w3.org/2000/01/rdf-schema#");
        ONTOLOGY_PREFIXES.add("http://www.w3.org/2001/XMLSchema#");
        ONTOLOGY_PREFIXES.add("http://imgpedia.dcc.uchile.cl/ontology#");
        ONTOLOGY_PREFIXES.add("http://dbpedia.org/ontology/");
        ONTOLOGY_PREFIXES.add("http://schema.org/");
        ONTOLOGY_PREFIXES.add("http://purl.org/dc/terms/");
        ONTOLOGY_PREFIXES.add("http://purl.org/vocommons/voaf#");
        ONTOLOGY_PREFIXES.add("http://purl.org/vocab/vann/");
        ONTOLOGY_PREFIXES.add("http://creativecommons.org/ns#");
    }

    private static final Pattern VALID_PERCENT_ENCODING = Pattern.compile("%[0-9A-Fa-f]{2}");

    /**
     * Constructs an EncodeIRI instance.
     * @param model The Jena Model to which valid triples will be added.
     */
    public EncodeIRI(Model model) {
        this.model = model;
    }

    /**
     * Processes and filters triples, normalizing IRIs and literals as needed.
     * @param triple The RDF triple to process.
     */
    @Override
    public void triple(Triple triple) {
        Node subject = normalizeNode(triple.getSubject());
        Node predicate = normalizeNode(triple.getPredicate());
        Node object = normalizeNode(triple.getObject());

        object = normalizeLiteralInf(object);

        if (!isHexSerializationSafe(subject) ||
            !isHexSerializationSafe(predicate) ||
            !isHexSerializationSafe(object)) {
            filteredTriples++;
            return;
        }

        if (endsWithColon(subject) || endsWithColon(predicate) || endsWithColon(object)) {
            filteredTriples++;
            return;
        }

        if (isValidIriNode(subject) && isValidIriNode(predicate) && isValidIriNode(object)) {
            model.getGraph().add(Triple.create(subject, predicate, object));
        } else {
            filteredTriples++;
        }
    }

    /**
     * Normalizes a literal node if it represents positive or negative infinity for float/double.
     * @param node The node to check and normalize.
     * @return The normalized node.
     */
    private Node normalizeLiteralInf(Node node) {
        if (!node.isLiteral()) {
            return node;
        }
        Node_Literal literal = (Node_Literal) node;
        String lexicalForm = literal.getLiteralLexicalForm();
        String datatypeUri = literal.getLiteralDatatypeURI();

        if (datatypeUri == null) {
            return node;
        }

        boolean isFloat = XSDDatatype.XSDfloat.getURI().equals(datatypeUri);
        boolean isDouble = XSDDatatype.XSDdouble.getURI().equals(datatypeUri);

        if ((isFloat || isDouble) && (lexicalForm.equalsIgnoreCase("inf") || lexicalForm.equalsIgnoreCase("+inf"))) {
            String maxVal = isFloat ? String.valueOf(Float.MAX_VALUE) : String.valueOf(Double.MAX_VALUE);
            return NodeFactory.createLiteral(maxVal, literal.getLiteralLanguage(), literal.getLiteralDatatype());
        } else if ((isFloat || isDouble) && lexicalForm.equalsIgnoreCase("-inf")) {
            String minVal = isFloat ? String.valueOf(-Float.MAX_VALUE) : String.valueOf(-Double.MAX_VALUE);
            return NodeFactory.createLiteral(minVal, literal.getLiteralLanguage(), literal.getLiteralDatatype());
        }
        return node;
    }

    /**
     * Checks if a node's string representation is safe for hex serialization.
     * @param node The node to check.
     * @return True if safe, false otherwise.
     */
    private boolean isHexSerializationSafe(Node node) {
        if (node.isURI()) {
            return isHexSafeString(node.getURI());
        }
        if (node.isLiteral()) {
            Node_Literal literal = (Node_Literal) node;
            if (!isHexSafeString(literal.getLiteralLexicalForm())) {
                return false;
            }
            if (literal.getLiteralLanguage() != null && !isHexSafeString(literal.getLiteralLanguage())) {
                return false;
            }
            if (literal.getLiteralDatatypeURI() != null && !isHexSafeString(literal.getLiteralDatatypeURI())) {
                return false;
            }
        }
        if (node.isBlank()) {
            return isHexSafeString(node.getBlankNodeLabel());
        }
        return true;
    }

    /**
     * Checks if a string contains only characters safe for hex serialization.
     * @param text The string to check.
     * @return True if safe, false otherwise.
     */
    private boolean isHexSafeString(String text) {
        if (text == null) return true;
        for (char c : text.toCharArray()) {
            if (isHexUnsafeChar(c)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Determines if a character is unsafe for hex serialization.
     * @param c The character to check.
     * @return True if unsafe, false otherwise.
     */
    private boolean isHexUnsafeChar(char c) {
        return c == '@' || c == '\\' || c == '^' || c == '`' ||
               c == '{' || c == '|' || c == '}' || c == '~' ||
               c < 0x20 || c == 0x7F ||
               (c >= 0x80 && c <= 0x9F) ||
               (c >= 0xA0 && c <= 0xFF) ||
               c > 127;
    }

    /**
     * Normalizes a node if it is a URI node, applying percent-encoding and other corrections.
     * @param node The node to normalize.
     * @return The normalized node.
     */
    private Node normalizeNode(Node node) {
        if (!node.isURI()) {
            return node;
        }
        String uri = node.getURI();

        if (isOntologyUri(uri)) {
            return node;
        }

        if (shouldNormalizeUri(uri)) {
            uri = fixUriPrefix(uri);
            uri = fixUriSuffix(uri);
            uri = trimUriBrackets(uri);

            if (uri.isEmpty()) {
                return NodeFactory.createURI("_INVALID_");
            }

            String normalizedUri = normalizeUri(uri);

            if (!isValidPercentEncoding(normalizedUri)) {
                return NodeFactory.createURI("_INVALID_");
            }
            if (!node.getURI().equals(normalizedUri)) {
                return NodeFactory.createURI(normalizedUri);
            }
        }
        return node;
    }

    /**
     * Checks if a URI should be normalized based on known resource patterns.
     * @param uri The URI to check.
     * @return True if it should be normalized, false otherwise.
     */
    private boolean shouldNormalizeUri(String uri) {
        return uri.contains("/resource/") || uri.contains("/images/") ||
               uri.contains("/wiki/File:") || uri.contains("/resource/File:");
    }

    /**
     * Fixes common prefix issues in URIs.
     * @param uri The URI to fix.
     * @return The fixed URI.
     */
    private String fixUriPrefix(String uri) {
        uri = uri.replaceFirst("^[0-9]+http", "http");
        uri = uri.replaceFirst("^http\\+/", "http://");
        int idx = uri.indexOf("http");
        if (idx > 0) {
            uri = uri.substring(idx);
        }
        return uri;
    }

    /**
     * Fixes common suffix issues in URIs.
     * @param uri The URI to fix.
     * @return The fixed URI.
     */
    private String fixUriSuffix(String uri) {
        if (uri.endsWith(":;")) {
            uri = uri.substring(0, uri.length() - 2) + ">";
        }
        if (uri.endsWith(":")) {
            uri = uri.substring(0, uri.length() - 1) + ">";
        }
        return uri;
    }

    /**
     * Trims leading '<' and excessive trailing '>' from a URI.
     * @param uri The URI to trim.
     * @return The trimmed URI.
     */
    private String trimUriBrackets(String uri) {
        while (uri.startsWith("<")) {
            uri = uri.substring(1);
        }
        while (uri.endsWith(">>")) {
            uri = uri.substring(0, uri.length() - 1);
        }
        return uri;
    }

    /**
     * Checks if a URI matches any known ontology prefix.
     * @param uri The URI to check.
     * @return True if it is an ontology URI, false otherwise.
     */
    private boolean isOntologyUri(String uri) {
        for (String prefix : ONTOLOGY_PREFIXES) {
            if (uri.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Applies percent-encoding to illegal characters in a URI.
     * @param uri The URI to normalize.
     * @return The percent-encoded URI.
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
     * Checks if a character is allowed in a URI without encoding.
     * @param c The character to check.
     * @return True if allowed, false otherwise.
     */
    private boolean isAllowedInUri(char c) {
        if (c == ' ' || c == '<' || c == '>' || c == '"' ||
            c == '{' || c == '}' || c == '|' || c == '\\' ||
            c == '^' || c == '`' || c == '\n' || c == '\r' ||
            c == '@' || c < 0x21 || c > 0x7E) {
            return false;
        }
        return (c >= 'A' && c <= 'Z') ||
               (c >= 'a' && c <= 'z') ||
               (c >= '0' && c <= '9') ||
               c == '-' || c == '.' || c == '_' || c == '~' ||
               c == ':' || c == '/' || c == '?' || c == '#' || c == '[' || c == ']' ||
               c == '!' || c == '$' || c == '&' || c == '\'' || c == '(' ||
               c == ')' || c == '*' || c == '+' || c == ',' || c == ';' || c == '=';
    }

    /**
     * Checks if a node is a valid IRI node.
     * @param node The node to check.
     * @return True if valid, false otherwise.
     */
    private boolean isValidIriNode(Node node) {
        if (!node.isURI()) return true;
        String uri = node.getURI();
        if ("_INVALID_".equals(uri)) return false;
        if (uri.endsWith(":")) return false;
        if (uri.matches(".*[ \"^@\\x00-\\x1F\\x7F].*")) return false;
        if (!isValidPercentEncoding(uri)) return false;
        return true;
    }

    /**
     * Checks if a node is a URI node ending with a colon.
     * @param node The node to check.
     * @return True if ends with colon, false otherwise.
     */
    private boolean endsWithColon(Node node) {
        return node.isURI() && node.getURI().endsWith(":");
    }

    /**
     * Checks if a URI string has valid percent encoding.
     * @param uri The URI string to check.
     * @return True if valid, false otherwise.
     */
    private boolean isValidPercentEncoding(String uri) {
        for (int i = 0; i < uri.length(); i++) {
            if (uri.charAt(i) == '%') {
                if (i + 2 >= uri.length() ||
                    !isHexDigit(uri.charAt(i + 1)) ||
                    !isHexDigit(uri.charAt(i + 2))) {
                    return false;
                }
                i += 2;
            }
        }
        return true;
    }

    /**
     * Checks if a character is a hexadecimal digit.
     * @param c The character to check.
     * @return True if hex digit, false otherwise.
     */
    private boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9') ||
               (c >= 'A' && c <= 'F') ||
               (c >= 'a' && c <= 'f');
    }

    /**
     * Returns the number of triples filtered out due to invalid encoding or unsafe content.
     * @return The count of filtered triples.
     */
    public int getFilteredTriplesCount() {
        return filteredTriples;
    }
}