/*
 * JBoss, Home of Professional Open Source
 * Copyright ${year}, Red Hat, Inc. and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.richfaces.renderkit;

import static org.richfaces.renderkit.RenderKitUtils.addToScriptHash;
import static org.richfaces.renderkit.RenderKitUtils.renderAttribute;
import static org.richfaces.renderkit.util.AjaxRendererUtils.AJAX_FUNCTION_NAME;
import static org.richfaces.renderkit.util.AjaxRendererUtils.buildAjaxFunction;
import static org.richfaces.renderkit.util.AjaxRendererUtils.buildEventOptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.faces.FacesException;
import javax.faces.application.ResourceDependencies;
import javax.faces.application.ResourceDependency;
import javax.faces.component.UIComponent;
import javax.faces.context.FacesContext;
import javax.faces.context.PartialResponseWriter;
import javax.faces.context.ResponseWriter;

import org.ajax4jsf.javascript.JSFunction;
import org.ajax4jsf.javascript.JSReference;
import org.ajax4jsf.javascript.ScriptUtils;
import org.ajax4jsf.model.DataVisitResult;
import org.ajax4jsf.model.DataVisitor;
import org.ajax4jsf.model.SequenceRange;
import org.richfaces.cdk.annotations.JsfRenderer;
import org.richfaces.component.AbstractExtendedDataTable;
import org.richfaces.component.UIDataTableBase;
import org.richfaces.component.util.HtmlUtil;
import org.richfaces.context.OnOffResponseWriter;
import org.richfaces.model.SelectionMode;
import org.richfaces.renderkit.RenderKitUtils.ScriptHashVariableWrapper;

/**
 * @author Konstantin Mishin
 *
 */


@JsfRenderer(type = "org.richfaces.ExtendedDataTableRenderer", family = AbstractExtendedDataTable.COMPONENT_FAMILY)
@ResourceDependencies({
    @ResourceDependency(library="org.richfaces", name = "extendedDataTable.ecss"),
    @ResourceDependency(library="org.richfaces", name = "ajax.reslib"),
    @ResourceDependency(name = "jquery.position.js"),
    @ResourceDependency(library="org.richfaces", name = "extendedDataTable.js") 
})
public class ExtendedDataTableRenderer extends SelectionRenderer implements MetaComponentRenderer {

    private static enum PartName {
        frozen, normal
    }
        
    private class Part {
        private PartName name;
        private List<UIComponent> columns;
        
        public Part(PartName name, List<UIComponent> columns) {
            this.name = name;
            this.columns = columns;
        }
        
        public PartName getName() {
            return name;
        }

        public List<UIComponent> getColumns() {
            return columns;
        }

    }
    
    private class RendererState extends RowHolderBase{

        private UIDataTableBase table;
        private List<Part> parts;
        private Part current;
        private Iterator<Part> partIterator;
        
        private EncoderVariance encoderVariance = EncoderVariance.full;
        
        public RendererState(FacesContext context, UIDataTableBase table) {
            super(context);
            this.table = table;
            List<UIComponent> frozenColumns;
            List<UIComponent> columns;
            columns = new ArrayList<UIComponent>();
            Iterator<UIComponent> iterator = table.columns();
            Map<String, UIComponent> columnsMap = new LinkedHashMap<String, UIComponent>();
            for (; iterator.hasNext();) {
                UIComponent component = iterator.next();
                if (component.isRendered()) {
                    columnsMap.put(component.getId(), component);
                }
            }            
            String[] columnsOrder = (String[]) table.getAttributes().get("columnsOrder");
            if (columnsOrder != null && columnsOrder.length > 0) {
                int i = 0;
                for (; i < columnsOrder.length && !columnsMap.isEmpty(); i++) {
                    columns.add(columnsMap.remove(columnsOrder[i]));
                }
            }
            iterator = columnsMap.values().iterator();
            for (; iterator.hasNext();) {
                columns.add(iterator.next());
            }
            int count = Math.min(((Integer) table.getAttributes().get("frozenColumns")).intValue(), columns.size());
            frozenColumns = columns.subList(0, count);
            columns = columns.subList(count, columns.size());
            parts = new ArrayList<Part>(PartName.values().length);
            if (frozenColumns.size() > 0) {
                parts.add(new Part(PartName.frozen, frozenColumns));
            }
            if (columns.size() > 0) {
                parts.add(new Part(PartName.normal, columns));
            }
        }

        public UIDataTableBase getRow() {
            return table;
        }

        public void startIterate() {
            partIterator = parts.iterator();
        }

        public Part nextPart() {
            current = partIterator.next();
            return current;
        }

        public Part getPart() {
            return current;
        }
        
        public boolean hasNextPart() {
            return partIterator.hasNext();
        }

        public EncoderVariance getEncoderVariance() {
            return encoderVariance;
        }
        
        public void setEncoderVariance(EncoderVariance encoderVariance) {
            this.encoderVariance = encoderVariance;
        }
    }

    private enum EncoderVariance {
        full {
            public void encodeStartUpdate(FacesContext context, String targetId) throws IOException {
                //do nothing
            }
            
            public void encodeEndUpdate(FacesContext context) throws IOException {
                //do nothing
            }
        }, 
        
        partial {

            private void switchResponseWriter(FacesContext context, boolean writerState) {
                ResponseWriter writer = context.getResponseWriter();
                ((OnOffResponseWriter) writer).setSwitchedOn(writerState);
            }

            public void encodeStartUpdate(FacesContext context, String targetId) throws IOException {
                switchResponseWriter(context, true);
                
                context.getPartialViewContext().getPartialResponseWriter().startUpdate(targetId);
            }

            public void encodeEndUpdate(FacesContext context) throws IOException {
                context.getPartialViewContext().getPartialResponseWriter().endUpdate();
                
                switchResponseWriter(context, false);
            }
        };

        public abstract void encodeStartUpdate(FacesContext context, String targetId) throws IOException;
        
        public abstract void encodeEndUpdate(FacesContext context) throws IOException;
        
    }
    
    private static final Map<java.lang.String, org.richfaces.renderkit.ComponentAttribute> EVENT_ATTRIBUTES
        = Collections.unmodifiableMap(ComponentAttribute.createMap(new ComponentAttribute("onselectionchange")
            .setEventNames(new String[] {"selectionchange"}), new ComponentAttribute("onbeforeselectionchange")
                .setEventNames(new String[] {"beforeselectionchange"})));

    private void encodeEmptyFooterCell(FacesContext context, ResponseWriter writer, UIComponent column)
        throws IOException {
        if (column.isRendered()) {
            writer.startElement(HtmlConstants.TD_ELEM, column);
            writer.startElement(HtmlConstants.DIV_ELEM, column);
            writer.writeAttribute(HtmlConstants.CLASS_ATTRIBUTE, "rf-edt-ftr-c-emp rf-edt-c-"
                + column.getId(), null);
            writer.endElement(HtmlConstants.DIV_ELEM);
            writer.endElement(HtmlConstants.TD_ELEM);
        }
    }

    private void encodeHeaderOrFooterCell(FacesContext context, ResponseWriter writer, UIComponent column,
        String facetName) throws IOException {
        if (column.isRendered()) {

            String classAttribute = facetName + "Class";
            writer.startElement(HtmlConstants.TD_ELEM, column);
            if ("header".equals(facetName)) {
                writer.startElement(HtmlConstants.DIV_ELEM, column);
                writer.writeAttribute(HtmlConstants.CLASS_ATTRIBUTE, "rf-edt-rsz-cntr rf-edt-c-"
                    + column.getId(), null);
                writer.startElement(HtmlConstants.DIV_ELEM, column);
                writer.writeAttribute(HtmlConstants.CLASS_ATTRIBUTE, "rf-edt-rsz", null);
                writer.endElement(HtmlConstants.DIV_ELEM);
                writer.endElement(HtmlConstants.DIV_ELEM);
            }
            writer.startElement(HtmlConstants.DIV_ELEM, column);
            writer.writeAttribute(
                HtmlConstants.CLASS_ATTRIBUTE,
                HtmlUtil.concatClasses("rf-edt-" + facetName.charAt(0) + facetName.charAt(3) + "r-c", "rf-edt-c-"
                    + column.getId(), (String) column.getAttributes().get(classAttribute)), null);
            writer.startElement(HtmlConstants.DIV_ELEM, column);
            writer.writeAttribute(HtmlConstants.CLASS_ATTRIBUTE, "rf-edt-" + facetName.charAt(0) + facetName.charAt(3)
                + "r-c-cnt", null);
            UIComponent facet = column.getFacet(facetName);
            if (facet != null && facet.isRendered()) {
                facet.encodeAll(context);
            }
            writer.endElement(HtmlConstants.DIV_ELEM);
            writer.endElement(HtmlConstants.DIV_ELEM);
            writer.endElement(HtmlConstants.TD_ELEM);
        }
    }

    private void encodeHeaderOrFooter(RendererState state, String name) throws IOException {
        FacesContext context = state.getContext();
        ResponseWriter writer = context.getResponseWriter();
        UIDataTableBase table = state.getRow();
        boolean columnFacetPresent = table.isColumnFacetPresent(name);
        if (columnFacetPresent || "footer".equals(name)) {
            writer.startElement(HtmlConstants.DIV_ELEM, table);
            writer.writeAttribute(HtmlConstants.CLASS_ATTRIBUTE, HtmlUtil.concatClasses("rf-edt-" + name.charAt(0)
                + name.charAt(3) + "r", (String) table.getAttributes().get(name + "Class")), null);
            writer.startElement(HtmlConstants.TABLE_ELEMENT, table);
            writer.writeAttribute(HtmlConstants.CELLPADDING_ATTRIBUTE, "0", null);
            writer.writeAttribute(HtmlConstants.CELLSPACING_ATTRIBUTE, "0", null);
            writer.startElement(HtmlConstants.TBODY_ELEMENT, table);
            writer.startElement(HtmlConstants.TR_ELEMENT, table);
            for (state.startIterate(); state.hasNextPart();) {
                Part part = state.nextPart();
                PartName partName = part.getName();
                Iterator<UIComponent> columns = part.getColumns().iterator();
                if (columns.hasNext()) {
                    writer.startElement(HtmlConstants.TD_ELEM, table);
                    if (PartName.frozen.equals(partName) && "footer".equals(name)) {
                        writer.writeAttribute(HtmlConstants.CLASS_ATTRIBUTE, "rf-edt-ftr-fzn", null);
                    }
                    writer.startElement(HtmlConstants.DIV_ELEM, table);
                    if (PartName.frozen.equals(partName)) {
                        if ("header".equals(name)) {
                            writer
                            .writeAttribute(HtmlConstants.ID_ATTRIBUTE, table.getClientId(context) + ":frozenHeader", null);
                        }
                    } else {
                        writer.writeAttribute(HtmlConstants.ID_ATTRIBUTE, table.getClientId(context) + ":" + name, null);
                        writer.writeAttribute(HtmlConstants.CLASS_ATTRIBUTE, "rf-edt-cnt"
                            + ("footer".equals(name) ? " rf-edt-ftr-cnt" : ""), null);
                    }

                    String tableId = table.getClientId(context) + ":cf" + name.charAt(0) + partName.name().charAt(0);
                    EncoderVariance encoderVariance = state.getEncoderVariance();
                    encoderVariance.encodeStartUpdate(context, tableId);

                    writer.startElement(HtmlConstants.TABLE_ELEMENT, table);
                    writer.writeAttribute(HtmlConstants.ID_ATTRIBUTE, tableId, null);
                    writer.writeAttribute(HtmlConstants.CELLPADDING_ATTRIBUTE, "0", null);
                    writer.writeAttribute(HtmlConstants.CELLSPACING_ATTRIBUTE, "0", null);
                    writer.startElement(HtmlConstants.TBODY_ELEMENT, table);
                    writer.startElement(HtmlConstants.TR_ELEMENT, table);
                    while (columns.hasNext()) {
                        if (columnFacetPresent) {
                            encodeHeaderOrFooterCell(context, writer, columns.next(), name);
                        } else {
                            encodeEmptyFooterCell(context, writer, columns.next());
                        }
                    }
                    writer.endElement(HtmlConstants.TR_ELEMENT);
                    writer.endElement(HtmlConstants.TBODY_ELEMENT);
                    writer.endElement(HtmlConstants.TABLE_ELEMENT);

                    encoderVariance.encodeEndUpdate(context);

                    writer.endElement(HtmlConstants.DIV_ELEM);
                    writer.endElement(HtmlConstants.TD_ELEM);
                }
            }
            writer.endElement(HtmlConstants.TR_ELEMENT);
            writer.endElement(HtmlConstants.TBODY_ELEMENT);
            writer.endElement(HtmlConstants.TABLE_ELEMENT);
            writer.endElement(HtmlConstants.DIV_ELEM);
        }
    }

    public void encodeHeader(RendererState state) throws IOException {
        FacesContext context = state.getContext();
        ResponseWriter writer = context.getResponseWriter();
        UIDataTableBase table = state.getRow();

        UIComponent header = table.getFacet("header");
        if (header != null && header.isRendered()) {
            String elementId = table.getClientId(context) + ":tfh";

            EncoderVariance encoderVariance = state.getEncoderVariance();
            encoderVariance.encodeStartUpdate(context, elementId);

            writer.startElement(HtmlConstants.DIV_ELEM, table);
            writer.writeAttribute(HtmlConstants.ID_ATTRIBUTE, elementId, null);
            writer.writeAttribute(HtmlConstants.CLASS_ATTRIBUTE, "rf-edt-tbl-hdr", null);
            header.encodeAll(context);
            writer.endElement(HtmlConstants.DIV_ELEM);

            encoderVariance.encodeEndUpdate(context);
        }

        encodeHeaderOrFooter(state, "header");
    }

    public void encodeBody(RendererState state) throws IOException {
        FacesContext context = state.getContext();
        ResponseWriter writer = context.getResponseWriter();
        UIDataTableBase table = state.getRow();
        String tableBodyId = table.getClientId(context) + ":b";
        EncoderVariance encoderVariance = state.getEncoderVariance();
        encoderVariance.encodeStartUpdate(context, tableBodyId);
        writer.startElement(HtmlConstants.DIV_ELEM, table);
        writer.writeAttribute(HtmlConstants.ID_ATTRIBUTE, tableBodyId, null);
        writer.writeAttribute(HtmlConstants.CLASS_ATTRIBUTE, "rf-edt-b", null);
        if (table.getRowCount() == 0) {
            UIComponent facet = table.getFacet("noData");
            if (facet != null && facet.isRendered()) {
                facet.encodeAll(context);
            } else {
                Object noDataLabel = table.getAttributes().get("noDataLabel");
                if (noDataLabel != null) {
                    writer.writeText(noDataLabel, "noDataLabel");
                }
            }
        } else {
            table.getAttributes().put("clientFirst", 0);
            writer.startElement(HtmlConstants.DIV_ELEM, table);
            writer.startElement(HtmlConstants.DIV_ELEM, table);
            writer.writeAttribute(HtmlConstants.CLASS_ATTRIBUTE, "rf-edt-spcr", null);
            writer.endElement(HtmlConstants.DIV_ELEM);
            writer.startElement(HtmlConstants.TABLE_ELEMENT, table);
            writer.writeAttribute(HtmlConstants.CELLPADDING_ATTRIBUTE, "0", null);
            writer.writeAttribute(HtmlConstants.CELLSPACING_ATTRIBUTE, "0", null);
            writer.startElement(HtmlConstants.TBODY_ELEMENT, table);
            writer.startElement(HtmlConstants.TR_ELEMENT, table);
            for (state.startIterate(); state.hasNextPart();) {
                writer.startElement(HtmlConstants.TD_ELEM, table);
                writer.startElement(HtmlConstants.DIV_ELEM, table);
                PartName partName = state.nextPart().getName();
                if (PartName.normal.equals(partName)) {
                    writer.writeAttribute(HtmlConstants.ID_ATTRIBUTE, table.getClientId(context) + ":body", null);
                    writer.writeAttribute(HtmlConstants.CLASS_ATTRIBUTE, "rf-edt-cnt", null);
                }
                String targetId = table.getClientId(context) + ":tbt" + partName.name().charAt(0);
                writer.startElement(HtmlConstants.TABLE_ELEMENT, table);
                writer.writeAttribute(HtmlConstants.ID_ATTRIBUTE, targetId, null);
                writer.writeAttribute(HtmlConstants.CELLPADDING_ATTRIBUTE, "0", null);
                writer.writeAttribute(HtmlConstants.CELLSPACING_ATTRIBUTE, "0", null);
                writer.startElement(HtmlConstants.TBODY_ELEMENT, table);
                writer.writeAttribute(HtmlConstants.ID_ATTRIBUTE, table.getClientId(context) + ":tb"
                    + partName.toString().charAt(0), null);
                encodeRows(context, state);
                writer.endElement(HtmlConstants.TBODY_ELEMENT);
                writer.endElement(HtmlConstants.TABLE_ELEMENT);

                writer.endElement(HtmlConstants.DIV_ELEM);
                writer.endElement(HtmlConstants.TD_ELEM);
            }
            writer.endElement(HtmlConstants.TR_ELEMENT);
            writer.endElement(HtmlConstants.TBODY_ELEMENT);
            writer.endElement(HtmlConstants.TABLE_ELEMENT);
            writer.endElement(HtmlConstants.DIV_ELEM);
        }
        writer.endElement(HtmlConstants.DIV_ELEM);
        encoderVariance.encodeEndUpdate(context);
    }

    public void encodeFooter(RendererState state) throws IOException {
        FacesContext context = state.getContext();
        ResponseWriter writer = context.getResponseWriter();
        UIDataTableBase table = state.getRow();

        encodeHeaderOrFooter(state, "footer");

        UIComponent footer = table.getFacet("footer");
        if (footer != null && footer.isRendered()) {
            String elementId = table.getClientId(context) + ":tff";

            EncoderVariance encoderVariance = state.getEncoderVariance();
            encoderVariance.encodeStartUpdate(context, elementId);

            writer.startElement(HtmlConstants.DIV_ELEM, table);
            writer.writeAttribute(HtmlConstants.ID_ATTRIBUTE, elementId, null);
            writer.writeAttribute(HtmlConstants.CLASS_ATTRIBUTE, "rf-edt-tbl-ftr", null);
            footer.encodeAll(context);
            writer.endElement(HtmlConstants.DIV_ELEM);

            encoderVariance.encodeEndUpdate(context);
        }
    }
    
    @Override
    protected Class<? extends UIComponent> getComponentClass() {
        return AbstractExtendedDataTable.class;
    }

    public void encodeMetaComponent(FacesContext context, UIComponent component, String metaComponentId) 
        throws IOException {
        AbstractExtendedDataTable table = (AbstractExtendedDataTable) component;
        if (AbstractExtendedDataTable.SCROLL.equals(metaComponentId)) {
            final PartialResponseWriter writer = context.getPartialViewContext().getPartialResponseWriter();
            int clientFirst = table.getClientFirst();
            Integer oldClientFirst = (Integer) table.getAttributes().remove(AbstractExtendedDataTable.OLD_CLIENT_FIRST);
            if (oldClientFirst == null) {
                oldClientFirst = clientFirst;
            }
            int clientRows = ((SequenceRange) table.getComponentState().getRange()).getRows();
            int difference = clientFirst - oldClientFirst;
            SequenceRange addRange = null;
            SequenceRange removeRange = null;
            if (Math.abs(difference) >= clientRows) {
                difference = 0;
                addRange = new SequenceRange(clientFirst, clientRows);
                removeRange = new SequenceRange(oldClientFirst, clientRows);
            } else if (difference < 0) {
                clientFirst += table.getFirst();
                addRange = new SequenceRange(clientFirst, -difference);
                removeRange = new SequenceRange(clientFirst + clientRows, -difference);
            } else if (difference > 0) {
                oldClientFirst += table.getFirst();
                removeRange = new SequenceRange(oldClientFirst, difference);
                int last = oldClientFirst + clientRows;
                addRange = new SequenceRange(last, difference);
            }
            if (addRange != null) {
                Object key = table.getRowKey();
                table.captureOrigValue(context);
                table.setRowKey(context, null);
                final RendererState state = createRowHolder(context, table, null);
                // TODO 1. Encode fixed children
                for (state.startIterate(); state.hasNextPart();) {
                    char partNameFirstChar = state.nextPart().getName().toString().charAt(0);
                    final List<String> ids = new LinkedList<String>();
                    table.walk(context, new DataVisitor() {
                        public DataVisitResult process(FacesContext context, Object rowKey, Object argument) {
                            UIDataTableBase dataTable = state.getRow();
                            dataTable.setRowKey(context, rowKey);
                            ids.add(dataTable.getClientId(context) + ":"
                                + state.getPart().getName().toString().charAt(0));
                            return DataVisitResult.CONTINUE;
                        }
                    }, removeRange, null);
                    table.walk(context, new DataVisitor() {
                        public DataVisitResult process(FacesContext context, Object rowKey, Object argument) {
                            UIDataTableBase dataTable = state.getRow();
                            dataTable.setRowKey(context, rowKey);
                            HashMap<String, String> attributes = new HashMap<String, String>(1);
                            String id = dataTable.getClientId(context) + ":"
                                + state.getPart().getName().toString().charAt(0);
                            attributes.put("id", id);
                            try {
                                writer.updateAttributes(ids.remove(0), attributes);
                                writer.startUpdate(id);
                                encodeRow(writer, context, state);
                                writer.endUpdate();
                            } catch (IOException e) {
                                throw new FacesException(e);
                            }
                            return DataVisitResult.CONTINUE;
                        }
                    },  addRange, null);
                    writer.startEval();
                    if (difference < 0) {
                        difference += clientRows;
                    }
                    
                    //TODO nick - move this to external JavaScript file
                    writer.write("var richTBody = document.getElementById('" + component.getClientId(context) + ":tb"
                        + partNameFirstChar + "');");
                    writer.write("var richRows = richTBody.rows;");
                    writer.write("for (var i = 0; i < " + difference
                        + "; i++ ) richTBody.appendChild(richTBody.removeChild(richRows[0]));");
                    writer.endEval();
                }
                writer.startUpdate(component.getClientId(context) + ":si");
                encodeSelectionInput(writer, context, component);
                writer.endUpdate();
                writer.startEval();
                writer.write("jQuery('#" + component.getClientId(context).replace(":", "\\\\:")
                    + "').triggerHandler('rich:onajaxcomplete', {first: " + table.getClientFirst() + "});");
                writer.endEval();
                table.setRowKey(context, key);
                table.restoreOrigValue(context);
            }
        } else {

            ResponseWriter initialWriter = context.getResponseWriter();
            assert !(initialWriter instanceof OnOffResponseWriter);

            try {
                context.setResponseWriter(new OnOffResponseWriter(initialWriter));

                RendererState state = createRowHolder(context, component, null);
                state.setEncoderVariance(EncoderVariance.partial);
                
                PartialResponseWriter writer = context.getPartialViewContext().getPartialResponseWriter();
                
                if (UIDataTableBase.HEADER.equals(metaComponentId)) {
                    encodeHeader(state);
                    writer.startEval();
                    writer.write("jQuery('#" + component.getClientId(context).replace(":", "\\\\:")
                        + "').triggerHandler('rich:onajaxcomplete', {reinitializeHeader: true});");
                    writer.endEval();
                } else if (UIDataTableBase.FOOTER.equals(metaComponentId)) {
                    encodeFooter(state);
                } else if (UIDataTableBase.BODY.equals(metaComponentId)) {
                    encodeBody(state);
                    writer.startUpdate(component.getClientId(context) + ":si");
                    encodeSelectionInput(writer, context, component);
                    writer.endUpdate();
                    writer.startEval();
                    writer.write("jQuery('#" + component.getClientId(context).replace(":", "\\\\:")
                        + "').triggerHandler('rich:onajaxcomplete', {first: " + table.getClientFirst() + ", rowCount: "
                        + getRowCount(component) + ", reinitializeBody: true});");
                    writer.endEval();
                } else {
                    throw new IllegalArgumentException("Unsupported metaComponentIdentifier: " + metaComponentId);
                }
            } finally {
                context.setResponseWriter(initialWriter);
            }
        }
    }
    
    public void decodeMetaComponent(FacesContext context, UIComponent component, String metaComponentId) {
        throw new UnsupportedOperationException();
    }

    protected void doEncodeBegin(ResponseWriter writer, FacesContext context, UIComponent component)
        throws IOException {
        Map<String, Object> attributes = component.getAttributes();
        writer.startElement(HtmlConstants.DIV_ELEM, component);
        writer.writeAttribute(HtmlConstants.ID_ATTRIBUTE, component.getClientId(context), null);
        writer.writeAttribute(HtmlConstants.CLASS_ATTRIBUTE, HtmlUtil.concatClasses("rf-edt",
            (String) attributes.get("styleClass")), null);
        renderAttribute(context, HtmlConstants.STYLE_ATTRIBUTE, attributes.get("style"));
    }

    public RendererState createRowHolder(FacesContext context, UIComponent component, Object[] options) {
        return new RendererState(context, (UIDataTableBase) component);
    }

    protected void doEncodeChildren(ResponseWriter writer, FacesContext context, UIComponent component)
        throws IOException {

        UIDataTableBase table = (UIDataTableBase) component;
        Object key = table.getRowKey();
        table.captureOrigValue(context);
        table.setRowKey(context, null);
        RendererState state = createRowHolder(context, table, null);
        encodeStyle(state);
        encodeHeader(state);
        encodeBody(state);
        encodeFooter(state);
        table.setRowKey(context, key);
        table.restoreOrigValue(context);
    }

    protected void doEncodeEnd(ResponseWriter writer, FacesContext context, UIComponent component)
        throws IOException {
        writer.startElement(HtmlConstants.TABLE_ELEMENT, component);
        writer.writeAttribute(HtmlConstants.ID_ATTRIBUTE, component.getClientId(context) + ":r", null);
        writer.writeAttribute(HtmlConstants.CLASS_ATTRIBUTE, "rf-edt-rord", null);
        writer.writeAttribute(HtmlConstants.CELLPADDING_ATTRIBUTE, "0", null);
        writer.writeAttribute(HtmlConstants.CELLSPACING_ATTRIBUTE, "0", null);
        writer.startElement(HtmlConstants.TR_ELEMENT, component);
        writer.startElement(HtmlConstants.TH_ELEM, component);
        writer.write("&#160;");
        writer.endElement(HtmlConstants.TH_ELEM);
        writer.endElement(HtmlConstants.TR_ELEMENT);
        for (int i = 0; i < 6; i++) {
            writer.startElement(HtmlConstants.TR_ELEMENT, component);
            writer.startElement(HtmlConstants.TD_ELEM, component);
            writer.write("&#160;");
            writer.endElement(HtmlConstants.TD_ELEM);
            writer.endElement(HtmlConstants.TR_ELEMENT);
        }
        writer.endElement(HtmlConstants.TABLE_ELEMENT);
        writer.startElement(HtmlConstants.DIV_ELEM, component);
        writer.writeAttribute(HtmlConstants.ID_ATTRIBUTE, component.getClientId(context) + ":d", null);
        writer.writeAttribute(HtmlConstants.CLASS_ATTRIBUTE, "rf-edt-rsz-mkr", null);
        writer.endElement(HtmlConstants.DIV_ELEM);
        writer.startElement(HtmlConstants.DIV_ELEM, component);
        writer.writeAttribute(HtmlConstants.ID_ATTRIBUTE, component.getClientId(context) + ":rm", null);
        writer.writeAttribute(HtmlConstants.CLASS_ATTRIBUTE, "rf-edt-rord-mkr", null);
        writer.endElement(HtmlConstants.DIV_ELEM);
        writer.startElement(HtmlConstants.INPUT_ELEM, component);
        writer.writeAttribute(HtmlConstants.ID_ATTRIBUTE, component.getClientId(context) + ":wi", null);
        writer.writeAttribute(HtmlConstants.NAME_ATTRIBUTE, component.getClientId(context) + ":wi", null);
        writer.writeAttribute(HtmlConstants.TYPE_ATTR, HtmlConstants.INPUT_TYPE_HIDDEN, null);
        writer.endElement(HtmlConstants.INPUT_ELEM);
        encodeSelectionInput(writer, context, component);
        JSFunction ajaxFunction = buildAjaxFunction(context, component, AJAX_FUNCTION_NAME);
        AjaxEventOptions eventOptions = buildEventOptions(context, component);
        Map<String, Object> parameters = eventOptions.getParameters();
        eventOptions.set(AjaxEventOptions.PARAMETERS, new JSReference("parameters"));
        ajaxFunction.addParameter(eventOptions);
        Map<String, Object> attributes = component.getAttributes();
        Map<String, Object> options = new HashMap<String, Object>();
        addToScriptHash(options, "parameters", parameters);
        addToScriptHash(options, "selectionMode", attributes.get("selectionMode"),
            SelectionMode.multiple);
        addToScriptHash(options, "onbeforeselectionchange", RenderKitUtils.getAttributeAndBehaviorsValue(context,
            component, EVENT_ATTRIBUTES.get("onbeforeselectionchange")), null, ScriptHashVariableWrapper.eventHandler);
        addToScriptHash(options, "onselectionchange", RenderKitUtils.getAttributeAndBehaviorsValue(context,
            component, EVENT_ATTRIBUTES.get("onselectionchange")), null, ScriptHashVariableWrapper.eventHandler);
        StringBuilder builder = new StringBuilder("new RichFaces.ExtendedDataTable('");
        builder.append(component.getClientId(context)).append("', ").append(getRowCount(component))
            .append(", function(event, parameters) {").append(ajaxFunction.toScript()).append(";}");
        if (!options.isEmpty()) {
            builder.append(",").append(ScriptUtils.toScript(options));
        }
        builder.append(");");
        getUtils().writeScript(context, component, builder.toString());
        writer.endElement(HtmlConstants.DIV_ELEM);
    }
    
    private int getRowCount(UIComponent component) {
        UIDataTableBase table = (UIDataTableBase) component;
        int rows = table.getRows();
        int rowCount = table.getRowCount() - table.getFirst();
        if (rows > 0) {
            rows = Math.min(rows, rowCount);
        } else {
            rows = rowCount;
        }

        return rows;
    }

    private void encodeStyle(RendererState state) throws IOException {
        FacesContext context = state.getContext();
        ResponseWriter writer = context.getResponseWriter();
        UIDataTableBase table = state.getRow();
        writer.startElement("style", table);
        writer.writeAttribute(HtmlConstants.TYPE_ATTR, "text/css", null);
        writer.writeText("div.rf-edt-cnt {", null); // TODO getNormalizedId(context, state.getGrid())
        writer.writeText("width: 100%;", "width");
        writer.writeText("}", null);
        Iterator<UIComponent> columns = table.columns();
        while (columns.hasNext()) {
            UIComponent column = (UIComponent) columns.next();
            String id = column.getId();
            if (id == null) {
                column.getClientId(context); // hack initialize id
                id = column.getId();
            }
            String width = getColumnWidth(column);
            writer.writeText(".rf-edt-c-" + id + " {", "width"); // TODO getNormalizedId(context,
            writer.writeText("width: " + width + ";", "width");
            writer.writeText("}", "width");
        }
        writer.endElement("style");
    }

    public void encodeRow(ResponseWriter writer, FacesContext facesContext, RowHolderBase rowHolder)
        throws IOException {
        RendererState state = (RendererState) rowHolder;
        UIDataTableBase table = state.getRow();
        writer.startElement(HtmlConstants.TR_ELEMENT, table);
        StringBuilder builder = new StringBuilder();
        Collection<Object> selection = table.getSelection();
        if (selection != null && selection.contains(table.getRowKey())) {
            builder.append("rf-edt-r-sel");
        }
        if (table.getRowKey().equals(table.getAttributes().get("activeRowKey"))) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append("rf-edt-r-act");
        }
        if (table.getRowKey().equals(table.getAttributes().get("shiftRowKey"))) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append("rf-edt-r-sht");
        }
        if (builder.length() > 0) {
            writer.writeAttribute(HtmlConstants.CLASS_ATTRIBUTE, builder.toString(), null);
        }
        Iterator<UIComponent> columns = null;
        Part part = state.getPart();
        writer.writeAttribute(HtmlConstants.ID_ATTRIBUTE,
            table.getClientId(facesContext) + ":" + part.getName().toString().charAt(0), null);
        columns = part.getColumns().iterator();
        while (columns.hasNext()) {
            UIComponent column = (UIComponent) columns.next();
            if (column.isRendered()) {
                writer.startElement(HtmlConstants.TD_ELEM, table);
                writer.startElement(HtmlConstants.DIV_ELEM, table);
                writer.writeAttribute(HtmlConstants.CLASS_ATTRIBUTE, "rf-edt-c rf-edt-c-"
                    + column.getId(), null);
                writer.startElement(HtmlConstants.DIV_ELEM, column);
                writer.writeAttribute(HtmlConstants.CLASS_ATTRIBUTE, "rf-edt-c-cnt", null);
                renderChildren(facesContext, column);
                writer.endElement(HtmlConstants.DIV_ELEM);
                writer.endElement(HtmlConstants.DIV_ELEM);
                writer.endElement(HtmlConstants.TD_ELEM);
            }
        }
        writer.endElement(HtmlConstants.TR_ELEMENT);
    }

    protected void doDecode(FacesContext context, UIComponent component) {
        super.doDecode(context, component);
        Map<String, String> map = context.getExternalContext().getRequestParameterMap();
        updateWidthOfColumns(context, component, map.get(component.getClientId(context) + ":wi"));
        if (map.get(component.getClientId(context)) != null) {
            updateColumnsOrder(context, component, map.get("rich:columnsOrder"));
        }
        if (map.get(component.getClientId(context)) != null) {
            updateClientFirst(context, component, map.get("rich:clientFirst"));
        }
        
        decodeSortingFiltering(context, component);

        /*
        if (map.get(component.getClientId(context)) != null) {
            decodeFiltering(context, component, map.get("rich:filterString"));
        }
        if (map.get(component.getClientId(context)) != null) {
            decodeSorting(context, component, map.get("rich:sortString"));
        } */ 
    }
    /*
    private void updateAttribute(FacesContext context, UIComponent component, String attribute, Object value) {
        Object oldValue = component.getAttributes().get(attribute);
        if ((oldValue != null && !oldValue.equals(value)) || (oldValue == null && value != null)) {
            ELContext elContext = context.getELContext();
            ValueExpression ve = component.getValueExpression(attribute);
            if (ve != null && !ve.isReadOnly(elContext)) {
                component.getAttributes().put(attribute, null);
                try {
                    ve.setValue(elContext, value);
                } catch (ELException e) {
                    throw new FacesException(e);
                }
            } else {
                component.getAttributes().put(attribute, value);
            }
        }
    }
    
    private void updateSortOrder(FacesContext context, UIComponent component, String value) {
        SortOrder sortOrder = SortOrder.ascending;
        try {
            sortOrder = SortOrder.valueOf(value);
        } catch (IllegalArgumentException e) {
            // If value isn't name of enum constant of SortOrder, toggle sortOrder of column.
            if (SortOrder.ascending.equals(component.getAttributes().get("sortOrder"))) {
                sortOrder = SortOrder.descending;
            }
        }
        updateAttribute(context, component, "sortOrder", sortOrder);
    }*/

    private void updateWidthOfColumns(FacesContext context, UIComponent component, String widthString) {
        if (widthString != null && widthString.length() > 0) {
            String[] widthArray = widthString.split(",");
            for (int i = 0; i < widthArray.length; i++) {
                String[] widthEntry = widthArray[i].split(":");
                UIComponent column = component.findComponent(widthEntry[0]);
                updateAttribute(context, column, "width", widthEntry[1]);
            }
        }
    }
    
    private void updateColumnsOrder(FacesContext context, UIComponent component, String columnsOrderString) {
        if (columnsOrderString != null && columnsOrderString.length() > 0) {
            String[] columnsOrder = columnsOrderString.split(",");
            updateAttribute(context, component, "columnsOrder", columnsOrder);
            context.getPartialViewContext().getRenderIds().add(component.getClientId(context)); //TODO Use partial re-rendering here.
        }
    }
    
    private void updateClientFirst(FacesContext context, UIComponent component, String clientFirst) {
        if (clientFirst != null && clientFirst.length() > 0) {
            Integer value = Integer.valueOf(clientFirst);
            Map<String, Object> attributes = component.getAttributes();
            if (!value.equals(attributes.get("clientFirst"))) {
                attributes.put(AbstractExtendedDataTable.SUBMITTED_CLIENT_FIRST, value);
                context.getPartialViewContext().getRenderIds().add(
                    component.getClientId(context) + "@" + AbstractExtendedDataTable.SCROLL);
            }
        }
    }
    
    /*
    private void decodeFiltering(FacesContext context, UIComponent component, String value) {
        if (value != null && value.length() > 0) {
            String[] values = value.split(":");
            if (Boolean.parseBoolean(values[2])) {
                UIDataTableBase table = (UIDataTableBase) component;
                for (Iterator<UIComponent> iterator = table.columns(); iterator.hasNext();) {
                    UIComponent column = iterator.next();
                    if (values[0].equals(column.getId())) {
                        updateAttribute(context, column, "filterValue", values[1]);
                    } else {
                        updateAttribute(context, column, "filterValue", null);
                    }
                }
            } else {
                updateAttribute(context, component.findComponent(values[0]), "filterValue", values[1]);
            }
            context.getPartialViewContext().getRenderIds().add(component.getClientId(context)); // TODO Use partial re-rendering here.
        }
    }
    *
    private void decodeSorting(FacesContext context, UIComponent component, String value) {
        if (value != null && value.length() > 0) {
            UIDataTableBase table = (UIDataTableBase) component;
            List<Object> sortPriority = new LinkedList<Object>();
            String[] values = value.split(":");
            if (Boolean.parseBoolean(values[2]) || SortMode.single.equals(table.getSortMode())) {
                for (Iterator<UIComponent> iterator = table.columns(); iterator.hasNext();) {
                    UIComponent column = iterator.next();
                    if (values[0].equals(column.getId())) {
                        updateSortOrder(context, column, values[1]);
                        sortPriority.add(values[0]);
                    } else {
                        updateAttribute(context, column, "sortOrder", SortOrder.UNSORTED);
                    }
                }
            } else {
                updateSortOrder(context, component.findComponent(values[0]), values[1]);
                Collection<?> priority = table.getSortPriority();
                if (priority != null) {
                    priority.remove(values[0]);
                    sortPriority.addAll(priority);
                }
                sortPriority.add(values[0]);
            }
            updateAttribute(context, component, "sortPriority", sortPriority);
            context.getPartialViewContext().getRenderIds().add(component.getClientId(context)); // TODO Use partial re-rendering here.
        }
    } */
    
    /**
     * @deprecated
     * TODO Remove this method when width in relative units in columns will be implemented.
     * @param column
     * @return width
     */
    private String getColumnWidth(UIComponent column) {
        String width = (String) column.getAttributes().get("width");
        if (width == null || width.length() == 0 || width.indexOf("%") != -1) {
            width = "100px";
        }
        return width;
    }
}
