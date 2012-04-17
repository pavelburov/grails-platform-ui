/* Copyright 2011-2012 the original author or authors:
 *
 *    Marc Palmer (marc@grailsrocks.com)
 *    Stéphane Maldini (stephane.maldini@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.plugin.platform

import org.codehaus.groovy.grails.plugins.web.taglib.ApplicationTagLib

import org.codehaus.groovy.grails.web.pages.FastStringWriter
import org.codehaus.groovy.grails.web.sitemesh.GSPSitemeshPage
import org.codehaus.groovy.grails.web.util.GrailsPrintWriter
import org.codehaus.groovy.grails.web.util.StreamCharBuffer
import com.opensymphony.module.sitemesh.RequestConstants

import org.grails.plugin.platform.util.TagLibUtils
import org.grails.plugin.platform.themes.Themes

class ThemeTagLib {
    static namespace = "theme"

    static REQ_ATTR_ZONE_LIST = 'grails.plugin.platform.zone.list'
    static REQ_ATTR_TITLE = 'grails.plugin.platform.title'
    
    static returnObjectForTags = ['name', 'current', 'list']

    def grailsThemes
    def grailsViewFinder
    def servletContext
    
    def layout = { attrs ->
        if (log.debugEnabled) {
            log.debug "Setting page's theme layout: ${attrs}"
        }
        if (!attrs.name) {
            attrs.name = Themes.DEFAULT_LAYOUT
        }
        grailsThemes.setRequestStyle(request, attrs.name)
        def layoutName = grailsThemes.getRequestSitemeshLayout(request)
        out << sitemesh.captureMeta(gsp_sm_xmlClosingForEmptyTag:"/", name:"layout",content:layoutName)
    }
    
    boolean isZoneDefined(id) {
        def zones = request[REQ_ATTR_ZONE_LIST]
        zones?.contains(id)
    }
    
    void doDefineZone(id) {
        def zones = request[REQ_ATTR_ZONE_LIST]
        if (!zones) {
            def s = new HashSet()
            s.add(id)
            request[REQ_ATTR_ZONE_LIST] = s
            return
        } else {
            if (!zones.contains(id)) {
                request[REQ_ATTR_ZONE_LIST] << id
            }
        }
    }
    
    def zone = { attrs, body->
        def id = attrs.name ?: 'body'
        doDefineZone(id)
    
        def htmlPage = getPage()
        def propertyName = id.toString()
        if(!(htmlPage instanceof GSPSitemeshPage)) {
            throwTagError("Tag [zone] requires 'grails.views.gsp.sitemesh.preprocess = true' -mode")
        }
        appendToContentBuffer(propertyName, body)
        return null
    }

    protected appendToContentBuffer(bufferName, body) {
        def htmlPage = getPage()
        def contentBuffer = htmlPage.getContentBuffer('page.' + bufferName)
        if(contentBuffer == null) {
            contentBuffer = wrapContentInBuffer(body)
            htmlPage.setContentBuffer(bufferName, contentBuffer)
        } else {
            new GrailsPrintWriter(contentBuffer.writer) << body()
        }
    }

    protected appendToHeadBuffer(body) {
        def htmlPage = getPage()
        def contentBuffer = htmlPage.headBuffer
        if(contentBuffer == null) {
            htmlPage.setHeadBuffer(wrapContentInBuffer(body))
        } else {
            new GrailsPrintWriter(contentBuffer.writer) << body()
        }
    }

    protected getPage() {
        return request[org.codehaus.groovy.grails.web.sitemesh.GrailsPageFilter.GSP_SITEMESH_PAGE]
    }

    protected wrapContentInBuffer(content) {
        if (content instanceof Closure) {
            content = content()
        }
        if (!(content instanceof StreamCharBuffer)) {
            // the body closure might be a string constant, so wrap it in a StreamCharBuffer in that case
            def newbuffer = new FastStringWriter()
            newbuffer.print(content)
            content = newbuffer.buffer
        }
        return content
    }
    
    private boolean isDebugMode() {
        request['plugin.platformUi.theme.debug.mode']
    }
    
    /**
     * Used by themes to render application-supplied content from zones defined in GSP pages or GSP templates.
     * Typically for "footer" or other common elements that would normally be in the application SiteMesh layout.
     * Themes may have varying support for these.
     * These also allow the page to override the content. The content is located so:
     * 1. A check is made for a "zone" with the name "template.<name>", if found this is rendered
     * 2. A check is made for a GSP template in the application at the path /_theme_templates/_<name>.gsp
     * 3. Failing (1) and (2), some default text is rendered in a <div> stating how to supply content for this element
     */
    def layoutZone = { attrs -> 
        mustBeInALayout('layoutZone')
        
        def id = attrs.name
        if (!id) {
            throwTagError "Tag [theme:layoutZone] requires a [name] attribute containing the name of the zone to render"
        }
        def zones = request[REQ_ATTR_ZONE_LIST]

        if (!zones || !zones.contains(id)) {
            def templatePath = "/_themes/_templates/${id}"
            
            // First see if the template provides default content for this zone (e.g. a footer or social panel)
            if (grailsViewFinder.templateExists(templatePath)) {
                out << g.render(template:templatePath) 
            } else {
                if (debugMode) {
                    out << dummyText(zone:id)
                } else {
                    if (log.warnEnabled) {
                        log.warn "Could not layout zone [$id], there is no content for it from the GSP page, and no application template at ${templatePath}"
                    }
                }
            }
        } else {
            out << g.pageProperty(name:'page.'+id)
        }
    }
    
    def name = { attrs ->
        out << grailsThemes.getRequestTheme(request).name
    }
    
    def list = { attrs ->
        grailsThemes.availableThemes.name
    }
    
    void mustBeInALayout(tagName) {
        if (!getPage()) {
            throwTagError "Tag [theme:$tagName] can only be used inside a Sitemesh layout" 
        }
    }
    
    def ifLayoutIs = { attrs, body ->
        if (!attrs.name) {
            throwTagError "Attribute [name] is required on the ifLayoutIs tag"
        }
        def style = grailsThemes.getRequestStyle(request)
        if (style == attrs.name) {
            out << body()
        }
    }
    
    def ifLayoutIsNot = { attrs, body ->
        if (!attrs.name) {
            throwTagError "Attribute [name] is required on the ifLayoutIsNot tag"
        }
        def style = grailsThemes.getRequestStyle(request)
        if (style != attrs.name) {
            out << body()
        }
    }
    
    def ifThemeIs = { attrs, body ->
        if (!attrs.name) {
            throwTagError "Attribute [name] is required on the ifThemeIs tag"
        }
        def theme = grailsThemes.getRequestTheme(request)
        if (theme.name == attrs.name) {
            out << body()
        }
    }
    
    def ifThemeIsNot = { attrs, body ->
        if (!attrs.name) {
            throwTagError "Attribute [name] is required on the ifThemeIsNot tag"
        }
        def theme = grailsThemes.getRequestTheme(request)
        if (theme.name != attrs.name) {
            out << body()
        }
    }
    
    def resources = { attrs ->
        if (log.debugEnabled) {
            log.debug "theme:resources Writing out resources"
        }
        def themeModules = []
        def theme = grailsThemes.getRequestTheme(request)
        out << r.require(modules:"theme.${theme.name}", strict:false) // @todo fix this
        def style = grailsThemes.getRequestStyle(request)
        out << r.require(modules:["theme.${theme.name}.${style}", "app.theme.${theme.name}", "app.theme.${theme.name}.${style}"], strict:false)
        // Write out the resources for the UI Set we are using, otherwise it will be too late if the lazy init does it in body
        out << ui.resources()
    }
    
    def current = { attrs ->
        return grailsThemes.getRequestTheme(request)
    }
    
    // @todo move this to TagLibUtils and use messageSource
    protected getMessageOrBody(Map attrs, Closure body) {
        def textCode = attrs.remove('text')
        def textCodeArgs = attrs.remove('textArgs')
        def textFromCode = textCode ? g.message(code:textCode, args:textCodeArgs) : null
        if (textFromCode) {
            textFromCode = textFromCode.encodeAsHTML()
        }
        def v = textFromCode ?: body()
        return v
    }
    
    // @todo Move to core
    def title = { attrs, body ->
        // @todo store just the args + body text so that if it is i18n we can resolve SEO title string by convention
        def text = getMessageOrBody(attrs, body)
        request[ThemeTagLib.REQ_ATTR_TITLE] = text
    }
    
    def layoutTitle = { attrs ->
        out << ui.h1(Collections.EMPTY_MAP, 
                request[ThemeTagLib.REQ_ATTR_TITLE] ?: g.layoutTitle(default:'Untitled') )
    }
    
    def layoutHTMLTitle = { attrs ->
        out << '<title>'
        def themeTitle = request[REQ_ATTR_TITLE]
        def title = g.layoutTitle(default:themeTitle ?: 'Untitled')
        out << title
        out << '</title>\n'
    }
    
    def head = { attrs, tagBody ->
        mustBeInALayout('head')

        out << "<head>\n"
        out << theme.layoutHTMLTitle()
        out << resources()
        out << tagBody()
        out << g.layoutHead()

        def resourceLinks = r.layoutResources()
        out << resourceLinks
        
        out << """</head>"""
    }

    def body = { attrs, tagBody ->
        mustBeInALayout('layoutZone')

        def bodyAttrs = attrs.bodyAttrs
        def bodyAttrsStr = ''
        if (bodyAttrs instanceof Map) {
            bodyAttrsStr = HTMLTagLib.attrsToString(bodyAttrs)
        } else if (bodyAttrs instanceof List) {
            def bodyAttrsMap = [:]
            bodyAttrs.each { p -> bodyAttrsMap[p] = g.pageProperty(name:'body.'+p) }
            bodyAttrsStr = HTMLTagLib.attrsToString(bodyAttrsMap)
        }
        out << "<body${bodyAttrsStr}>"
        if (debugMode) {
            // We need the body of the debug GSP as it has the panel in it
            // @todo we can probably ditch this layoutBody if theme previewer concats to "body" zone
            out << g.layoutBody()
            out << tagBody()
        } else {
            if (!isZoneDefined('body')) {
                out << g.layoutBody()
            } else {
                // @todo check for empty body and if so just render out 'body' zone by default
                out << tagBody()
            }
        }
        out << r.layoutResources() 
        out << """</body>"""
    }

    def layoutTemplate = { attrs ->
        def templateView = grailsThemes.getRequestThemeTemplateView(request, attrs.name)
        if (log.debugEnabled) {
            log.debug "Resolved current request's theme template for [${attrs.name}] to [${templateView}]"
        }
        out << g.render(template:templateView.path, plugin:templateView.plugin)
    }

    def dummyText = { attrs -> 
        if (!attrs.zone) {
            throwTagError "The attribute 'zone' is required to denote the zone for which dummy text is required"
        }
        def view = grailsThemes.getDummyTextTemplateForZone(request, attrs.zone)
        if (view) {
            out << g.render(template:view.path, plugin:view.plugin) 
        } else {
            out << ui.h2([:], "Zone [${attrs.zone}]")
            out << p.dummyText(size:1)
        }
    }
    
    def debugMode = { attrs ->
        request['plugin.platformUi.theme.debug.mode'] = true
    }
    

}