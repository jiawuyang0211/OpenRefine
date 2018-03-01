package org.openrefine.wikidata.schema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jsoup.helper.Validate;
import org.openrefine.wikidata.qa.QAWarning;
import org.openrefine.wikidata.schema.exceptions.SkipSchemaExpressionException;
import org.wikidata.wdtk.datamodel.helpers.Datamodel;
import org.wikidata.wdtk.datamodel.interfaces.Claim;
import org.wikidata.wdtk.datamodel.interfaces.ItemIdValue;
import org.wikidata.wdtk.datamodel.interfaces.Reference;
import org.wikidata.wdtk.datamodel.interfaces.Snak;
import org.wikidata.wdtk.datamodel.interfaces.SnakGroup;
import org.wikidata.wdtk.datamodel.interfaces.PropertyIdValue;
import org.wikidata.wdtk.datamodel.interfaces.Statement;
import org.wikidata.wdtk.datamodel.interfaces.StatementRank;
import org.wikidata.wdtk.datamodel.interfaces.Value;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class WbStatementExpr {
    
    private WbExpression<? extends Value> mainSnakValueExpr;
    private List<WbSnakExpr> qualifierExprs;
    private List<WbReferenceExpr> referenceExprs;
    
    @JsonCreator
    public WbStatementExpr(
            @JsonProperty("value") WbExpression<? extends Value> mainSnakValueExpr,
            @JsonProperty("qualifiers") List<WbSnakExpr> qualifierExprs,
            @JsonProperty("references") List<WbReferenceExpr> referenceExprs) {
        Validate.notNull(mainSnakValueExpr);
        this.mainSnakValueExpr = mainSnakValueExpr;
        if (qualifierExprs == null) {
            qualifierExprs = Collections.emptyList();
        }
        this.qualifierExprs = qualifierExprs;
        if (referenceExprs == null) {
            referenceExprs = Collections.emptyList();
        }
        this.referenceExprs = referenceExprs;
    }
    
    public static List<SnakGroup> groupSnaks(List<Snak> snaks) {
        List<SnakGroup> snakGroups = new ArrayList<SnakGroup>();
        for (Snak snak : snaks) {
            List<Snak> singleton = new ArrayList<Snak>();
            singleton.add(snak);
            snakGroups.add(Datamodel.makeSnakGroup(singleton));
        }
        return snakGroups;
    }
    
    public Statement evaluate(ExpressionContext ctxt, ItemIdValue subject, PropertyIdValue propertyId)
            throws SkipSchemaExpressionException {
        Value mainSnakValue = getMainsnak().evaluate(ctxt);
        Snak mainSnak = Datamodel.makeValueSnak(propertyId, mainSnakValue);
        
        // evaluate qualifiers
        List<Snak> qualifiers = new ArrayList<Snak>(getQualifiers().size());
        for (WbSnakExpr qExpr : getQualifiers()) {
            try {
                qualifiers.add(qExpr.evaluate(ctxt));
            } catch(SkipSchemaExpressionException e) {
                QAWarning warning = new QAWarning(
                        "ignored-qualifiers",
                        null,
                       QAWarning.Severity.INFO,
                       1);
                warning.setProperty("example_entity", subject);
                warning.setProperty("example_property_entity", mainSnak.getPropertyId());
                ctxt.addWarning(warning);
            }
        }
        List<SnakGroup> groupedQualifiers = groupSnaks(qualifiers);
        Claim claim = Datamodel.makeClaim(subject, mainSnak, groupedQualifiers);
        
        // evaluate references
        List<Reference> references = new ArrayList<Reference>();
        for (WbReferenceExpr rExpr : getReferences()) {
            try {
                references.add(rExpr.evaluate(ctxt));
            } catch(SkipSchemaExpressionException e) {
                QAWarning warning = new QAWarning(
                        "ignored-references",
                        null,
                       QAWarning.Severity.INFO,
                       1);
                warning.setProperty("example_entity", subject);
                warning.setProperty("example_property_entity", mainSnak.getPropertyId());
                ctxt.addWarning(warning);
            }
        }
        
        StatementRank rank = StatementRank.NORMAL;
        return Datamodel.makeStatement(claim, references, rank, "");
    }

    @JsonProperty("value")
    public WbExpression<? extends Value> getMainsnak() {
        return mainSnakValueExpr;
    }

    @JsonProperty("qualifiers")
    public List<WbSnakExpr> getQualifiers() {
        return qualifierExprs;
    }

    @JsonProperty("references")
    public List<WbReferenceExpr> getReferences() {
        return referenceExprs;
    }
    
    @Override
    public boolean equals(Object other) {
        if (other == null || !WbStatementExpr.class.isInstance(other)) {
            return false;
        }
        WbStatementExpr otherExpr = (WbStatementExpr)other;
        return mainSnakValueExpr.equals(otherExpr.getMainsnak()) &&
                qualifierExprs.equals(otherExpr.getQualifiers()) &&
                referenceExprs.equals(otherExpr.getReferences());
    }
    
    @Override
    public int hashCode() {
        return mainSnakValueExpr.hashCode() + qualifierExprs.hashCode() + referenceExprs.hashCode();
    }
}