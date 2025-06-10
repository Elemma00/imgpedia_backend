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

public class EncodeIRI extends StreamRDFBase {
    private final Model model;
    private int filteredTriples = 0; // Contador de triples filtrados

    // Ontology prefixes to never modify
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

    // Pattern for valid percent encoding: % followed by two hex digits
    private static final Pattern VALID_PERCENT_ENCODING = Pattern.compile("%[0-9A-Fa-f]{2}");

    public EncodeIRI(Model model) {
        this.model = model;
    }

    @Override
    public void triple(Triple triple) {
        Node subject = normalizeNode(triple.getSubject());
        Node predicate = normalizeNode(triple.getPredicate());
        Node object = normalizeNode(triple.getObject());

        // Reemplazo de literales "inf" para float/double
        if (object.isLiteral()) {
            Node_Literal lit = (Node_Literal) object;
            String lex = lit.getLiteralLexicalForm();
            String dtype = lit.getLiteralDatatypeURI();
            if (dtype != null) {
                if ((XSDDatatype.XSDfloat.getURI().equals(dtype) || XSDDatatype.XSDdouble.getURI().equals(dtype))
                    && (lex.equalsIgnoreCase("inf") || lex.equalsIgnoreCase("+inf"))) {
                    // Reemplaza por el valor máximo
                    String maxVal = XSDDatatype.XSDfloat.getURI().equals(dtype)
                            ? String.valueOf(Float.MAX_VALUE)
                            : String.valueOf(Double.MAX_VALUE);
                    object = NodeFactory.createLiteral(maxVal, lit.getLiteralLanguage(), lit.getLiteralDatatype());
                } else if ((XSDDatatype.XSDfloat.getURI().equals(dtype) || XSDDatatype.XSDdouble.getURI().equals(dtype))
                    && lex.equalsIgnoreCase("-inf")) {
                    // Reemplaza por el valor mínimo negativo
                    String minVal = XSDDatatype.XSDfloat.getURI().equals(dtype)
                            ? String.valueOf(-Float.MAX_VALUE)
                            : String.valueOf(-Double.MAX_VALUE);
                    object = NodeFactory.createLiteral(minVal, lit.getLiteralLanguage(), lit.getLiteralDatatype());
                }
            }
        }

        // NUEVA VALIDACIÓN: Filtra triples que causarán problemas en serialización hex
        if (!isHexSerializationSafe(subject) || 
            !isHexSerializationSafe(predicate) || 
            !isHexSerializationSafe(object)) {
            filteredTriples++;
            return; // Elimina este triple
        }

        if ((subject.isURI() && subject.getURI().endsWith(":")) ||
            (predicate.isURI() && predicate.getURI().endsWith(":")) ||
            (object.isURI() && object.getURI().endsWith(":"))) {
            filteredTriples++;
            return; 
        }

        if (isValidIriNode(subject) && isValidIriNode(predicate) && isValidIriNode(object)) {
            model.getGraph().add(Triple.create(subject, predicate, object));
        } else {
            filteredTriples++;
        }
    }

    // NUEVA FUNCIÓN: Verifica si un nodo es seguro para serialización hexadecimal
    private boolean isHexSerializationSafe(Node node) {
        if (node.isURI()) {
            return isHexSafeString(node.getURI());
        } else if (node.isLiteral()) {
            Node_Literal lit = (Node_Literal) node;
            
            // Verifica el valor literal
            if (!isHexSafeString(lit.getLiteralLexicalForm())) {
                return false;
            }
            
            // Verifica el idioma si existe
            if (lit.getLiteralLanguage() != null && 
                !isHexSafeString(lit.getLiteralLanguage())) {
                return false;
            }
            
            // Verifica el tipo de dato si existe
            if (lit.getLiteralDatatypeURI() != null && 
                !isHexSafeString(lit.getLiteralDatatypeURI())) {
                return false;
            }
        } else if (node.isBlank()) {
            return isHexSafeString(node.getBlankNodeLabel());
        }
        
        return true;
    }

    // NUEVA FUNCIÓN: Verifica si una cadena es segura para serialización hexadecimal
    private boolean isHexSafeString(String text) {
        if (text == null) return true;
        
        for (char c : text.toCharArray()) {
            // Excluye caracteres que causan "Bad hex char" en hexRead()
            if (c == '@' ||           // 0x40 - "Bad hex char : 64"
                c == '\\' ||          // 0x5C - "Bad hex char : 92" 
                c == '^' ||           // 0x5E - "Bad hex char : 94"
                c == '`' ||           // 0x60 - "Bad hex char : 96"
                c == '{' ||           // 0x7B - "Bad hex char : 123"
                c == '|' ||           // 0x7C - "Bad hex char : 124"
                c == '}' ||           // 0x7D - "Bad hex char : 125"
                c == '~' ||           // 0x7E - "Bad hex char : 126"
                c < 0x20 ||           // Caracteres de control (0-31)
                c == 0x7F ||          // DEL (127)
                (c >= 0x80 && c <= 0x9F) ||  // Caracteres de control extendidos (128-159)
                (c >= 0xA0 && c <= 0xFF)) {   // Caracteres no-ASCII (160-255)
                return false;
            }
            
            // También verifica caracteres que no son hex válidos cuando se serializan
            if (c > 127) {  // Cualquier carácter no-ASCII puede ser problemático
                return false;
            }
        }
        return true;
    }

    private Node normalizeNode(Node node) {
        if (node.isURI()) {
            String uri = node.getURI();

            // No modificar si es una ontología conocida
            if (isOntologyUri(uri)) {
                return node;
            }

            // Solo modificar recursos (IMGpedia, Wikimedia Commons, DBpedia, etc.)
            if (uri.contains("/resource/") || uri.contains("/images/") ||
                uri.contains("/wiki/File:") || uri.contains("/resource/File:")) {

                // Corrige IRIs que empiezan con un número (ej: 8http...) o sin 'http'
                uri = uri.replaceFirst("^[0-9]+http", "http");
                uri = uri.replaceFirst("^http\\+/", "http://");
                int idx = uri.indexOf("http");
                if (idx > 0) {
                    uri = uri.substring(idx);
                }

               if (uri.endsWith(":;")) {
                    uri = uri.substring(0, uri.length() - 2) + ">";
                }
                // Corrige IRIs que terminan con :
                if (uri.endsWith(":")) {
                    uri = uri.substring(0, uri.length() - 1) + ">";
                }

                // Elimina > o < sueltos al final/inicio, pero deja el > si fue puesto por la corrección anterior
                while (uri.startsWith("<")) uri = uri.substring(1);
                // Solo elimina > si hay más de uno al final
                while (uri.endsWith(">>")) uri = uri.substring(0, uri.length() - 1);

                // Si la IRI queda vacía, márcala como inválida
                if (uri.isEmpty()) {
                    return NodeFactory.createURI("_INVALID_");
                }
                // Aplica percent-encoding a caracteres ilegales
                String normalizedUri = normalizeUri(uri);

                // Si el percent encoding es inválido, retorna un nodo inválido
                if (!isValidPercentEncoding(normalizedUri)) {
                    return NodeFactory.createURI("_INVALID_");
                }
                if (!node.getURI().equals(normalizedUri)) {
                    return NodeFactory.createURI(normalizedUri);
                }
            }
        }
        return node;
    }

    private boolean isOntologyUri(String uri) {
        for (String prefix : ONTOLOGY_PREFIXES) {
            if (uri.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

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

    private boolean isAllowedInUri(char c) {
        // ACTUALIZADO: Excluye caracteres problemáticos para hex serialization
        if (c == ' ' || c == '<' || c == '>' || c == '"' ||
            c == '{' || c == '}' || c == '|' || c == '\\' ||
            c == '^' || c == '`' || c == '\n' || c == '\r' ||
            c == '@' ||           // NUEVO: Excluye @ que causa "Bad hex char : 64"
            c < 0x21 || c > 0x7E) {
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

    private boolean isValidIriNode(Node node) {
        if (!node.isURI()) return true;
        String uri = node.getURI();
        // Si fue marcado como inválido, ignora el triple
        if ("_INVALID_".equals(uri)) return false;
        // No termina en :
        if (uri.endsWith(":")) return false;
        // No contiene espacios, comillas, ^, o caracteres de control
        if (uri.matches(".*[ \"^@\\x00-\\x1F\\x7F].*")) return false; // ACTUALIZADO: incluye @
        // Percent encoding debe ser válido
        if (!isValidPercentEncoding(uri)) return false;
        return true;
    }

    private boolean isValidPercentEncoding(String uri) {
        // Busca todos los % en la URI y verifica que estén seguidos de dos hex
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

    private boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9') ||
               (c >= 'A' && c <= 'F') ||
               (c >= 'a' && c <= 'f');
    }

    // NUEVA FUNCIÓN: Obtiene estadísticas de filtrado
    public int getFilteredTriplesCount() {
        return filteredTriples;
    }
}