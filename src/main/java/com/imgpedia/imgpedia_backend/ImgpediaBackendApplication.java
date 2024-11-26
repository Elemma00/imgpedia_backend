package com.imgpedia.imgpedia_backend;

import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.query.Syntax;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.sparql.algebra.Algebra;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.engine.QueryIterator;
import org.apache.jena.sparql.engine.binding.Binding;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ImgpediaBackendApplication {

    public static void Ejemplo1() {
        // Create an empty model
        Model model = ModelFactory.createDefaultModel();

        RDFDataMgr.read(model, "./rdfs/vec3.rdf");

        // Define the SPARQL query with similarity join
        String queryString = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n"
                + "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n"
                + "PREFIX v: <http://example.org/v#>\n"
                + "PREFIX ns1: <http://example.org/ns#>\n"
                + "PREFIX sim: <http://sj.dcc.uchile.cl/sim#>\n"
                + "PREFIX imo: <http://imgpedia.dcc.uchile.cl/ontology#>\n"
                + "PREFIX wd: <http://www.wikidata.org/entity/>\n"
                + "PREFIX wdt: <http://www.wikidata.org/prop/direct/>\n"
                + "SELECT ?x ?y ?d WHERE {\n"
                + "   {?x v:valor ?xv }\n"
                + "   SIMILARITY JOIN ON (?xv) (?yv) TOP 3 DISTANCE sim:manhattanvec AS ?d\n"
                + "   {?y v:valor ?yv}\n"
                + "}";

        Query query = QueryFactory.create(queryString, Syntax.syntaxSPARQL_11_sim);

        try (QueryExecution qexec = QueryExecutionFactory.create(query, model)) {
            ResultSet results = qexec.execSelect();
            ResultSetFormatter.out(System.out, results, query);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void Ejemplo2(){
        Model m = createSimModel();//dataset.getDefaultModel();
        String s = "PREFIX wdt:<http://www.wikidata.org/prop/direct/>\n"
                + "PREFIX wd:<http://www.wikidata.org/entity/>\n"
                + "PREFIX ex:<http://ex.com/>\n"
                + "PREFIX sim:<http://sj.dcc.uchile.cl/sim#>\n"
                + "SELECT ?a ?b ?c WHERE {"
                + "?c1 ex:a ?a ; "
                + "ex:b ?b .} "
                + "CLUSTER BY ?a ?b "
                + "WITH sim:kmeans(3, 11)	 AS ?c";
        Query query = QueryFactory.create(s, Syntax.syntaxSPARQL_11_sim);

        Op op = Algebra.compile(query);

        //System.out.println(op);
        QueryIterator qIter = Algebra.exec(op, m);
        while (qIter.hasNext()) {
            Binding b = qIter.nextBinding();
            System.out.println(b);
        }
    }

    public static Model createSimModel() {
        Model m = ModelFactory.createDefaultModel();
        Property a = m.createProperty("http://ex.com/a");
        Property b = m.createProperty("http://ex.com/b");
        int N = 100;
        for (int i = 0; i < N; i++) {
            Resource r = m.createResource("http://ex.com/" + i);
            r.addProperty(a, "" + i, XSDDatatype.XSDdouble).addProperty(b, "" + i, XSDDatatype.XSDdouble);
        }
        return m;
    }
    
    public static void main(String[] args) {
        SpringApplication.run(ImgpediaBackendApplication.class, args);
        // Ejemplo1();
        // System.out.println("===================================================");
        // Ejemplo2();
    }
        
}
