// $ANTLR 3.2 Sep 23, 2009 12:02:23 org/brackit/server/node/index/definition/IndexDef.g 2011-05-06 12:14:42

/*
 * [New BSD License]
 * Copyright (c) 2011, Brackit Project Team <info@brackit.org>  
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the <organization> nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.brackit.server.node.index.definition;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.brackit.xquery.xdm.Type;

import org.brackit.xquery.util.path.Path;


import org.antlr.runtime.*;
import java.util.Stack;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
public class IndexDefParser extends Parser {
    public static final String[] tokenNames = new String[] {
        "<invalid>", "<EOR>", "<DOWN>", "<UP>", "CREATE", "TERMINATOR", "CONTENT", "INDEX", "UNIQUE", "CAS", "PATH", "ELEMENT", "LPAREN", "INCLUDING", "COMMA", "RPAREN", "EXCLUDING", "AT", "SPLID", "PCR", "FOR", "ATTRIBUTE", "ALL", "WITH", "CLUSTERING", "OF_TYPE", "STRING", "DOUBLE", "LONG", "INTEGER", "PATHS", "ON", "CHILD_ATT", "WILDCARD", "NCNAME", "DESC_ATT", "CHILD", "DESC", "IN", "DIGITS", "COLON", "SELF", "PARENT", "DOT", "DCOLON", "UNDERSCORE", "DASH", "WHITESPACE", "INS", "NEWLINE", "LETTER", "NCNAME_CHAR", "DIGIT", "COMBINING_CHAR", "EXTENDER", "BASE_CHAR", "IDEOGRAPHIC"
    };
    public static final int LETTER=50;
    public static final int DIGITS=39;
    public static final int PCR=19;
    public static final int ATTRIBUTE=21;
    public static final int FOR=20;
    public static final int CAS=9;
    public static final int EOF=-1;
    public static final int SPLID=18;
    public static final int LPAREN=12;
    public static final int AT=17;
    public static final int INDEX=7;
    public static final int RPAREN=15;
    public static final int PARENT=42;
    public static final int DCOLON=44;
    public static final int CREATE=4;
    public static final int IN=38;
    public static final int COMMA=14;
    public static final int PATH=10;
    public static final int DESC_ATT=35;
    public static final int PATHS=30;
    public static final int WILDCARD=33;
    public static final int ALL=22;
    public static final int ELEMENT=11;
    public static final int DOUBLE=27;
    public static final int IDEOGRAPHIC=56;
    public static final int DIGIT=52;
    public static final int DOT=43;
    public static final int CLUSTERING=24;
    public static final int WITH=23;
    public static final int INTEGER=29;
    public static final int CHILD=36;
    public static final int UNIQUE=8;
    public static final int DASH=46;
    public static final int OF_TYPE=25;
    public static final int TERMINATOR=5;
    public static final int BASE_CHAR=55;
    public static final int ON=31;
    public static final int WHITESPACE=47;
    public static final int UNDERSCORE=45;
    public static final int INS=48;
    public static final int CONTENT=6;
    public static final int EXTENDER=54;
    public static final int EXCLUDING=16;
    public static final int NCNAME_CHAR=51;
    public static final int CHILD_ATT=32;
    public static final int COLON=40;
    public static final int NCNAME=34;
    public static final int NEWLINE=49;
    public static final int COMBINING_CHAR=53;
    public static final int DESC=37;
    public static final int LONG=28;
    public static final int SELF=41;
    public static final int INCLUDING=13;
    public static final int STRING=26;

    // delegates
    // delegators


        public IndexDefParser(TokenStream input) {
            this(input, new RecognizerSharedState());
        }
        public IndexDefParser(TokenStream input, RecognizerSharedState state) {
            super(input, state);
             
        }
        

    public String[] getTokenNames() { return IndexDefParser.tokenNames; }
    public String getGrammarFileName() { return "org/brackit/server/node/index/definition/IndexDef.g"; }


    	// coustom constructor to skip lexer and token stream creation
    	public IndexDefParser(Reader in) throws IOException
    	{
    		this(new CommonTokenStream(new IndexDefLexer(new ANTLRReaderStream(in))));
    	}

    	private class ElementAttributeBooleans
    	{
    		public final boolean element;
    		public final boolean attribute;
    		
    		public ElementAttributeBooleans(boolean element, boolean attribute)
    		{
    			this.element = element;
    			this.attribute = attribute;
    		}
    	}

    	private int toInt(String s)
    	{
    		try
    		{
    			int i = Integer.valueOf(s);
    			return i;
    		}
    		catch (NumberFormatException e)
    		{
    			throw new RuntimeException("Unexpected parsing error: Could not convert to integer (Please fix grammar file)");
    		}
    	}
    	
    	// raise exception when mismatched token occurs
    	protected Object recoverFromMismatchedToken(IntStream input, int ttype, BitSet follow) throws RecognitionException
    	{
    		throw new MismatchedTokenException(ttype, input);
    	}



    // $ANTLR start "index"
    // org/brackit/server/node/index/definition/IndexDef.g:133:1: index returns [IndexDef index] : newIdx= indexDefinition[h] EOF ;
    public final IndexDef index() throws RecognitionException {
        IndexDef index = null;

        IndexDef newIdx = null;



        		IndexDefHelper h = new IndexDefHelper();
        		index = null;
        	
        try {
            // org/brackit/server/node/index/definition/IndexDef.g:143:2: (newIdx= indexDefinition[h] EOF )
            // org/brackit/server/node/index/definition/IndexDef.g:143:4: newIdx= indexDefinition[h] EOF
            {
            pushFollow(FOLLOW_indexDefinition_in_index70);
            newIdx=indexDefinition(h);

            state._fsp--;
            if (state.failed) return index;
            if ( state.backtracking==0 ) {
               index = newIdx; 
            }
            match(input,EOF,FOLLOW_EOF_in_index75); if (state.failed) return index;

            }

        }

        	catch (RecognitionException re)
        	{
        		// TODO: set the reported error message as the exception message
        		reportError(re);
        		throw re;
        	}
        finally {
        }
        return index;
    }
    // $ANTLR end "index"


    // $ANTLR start "indexDefinition"
    // org/brackit/server/node/index/definition/IndexDef.g:146:1: indexDefinition[IndexDefHelper h] returns [IndexDef result] : CREATE (newIdx= contentIndex[h] | newIdx= casIndex[h] | newIdx= pathIndex[h] | newIdx= elementIndex[h] ) (c= inContainer )? ( TERMINATOR )? ;
    public final IndexDef indexDefinition(IndexDefHelper h) throws RecognitionException {
        IndexDef result = null;

        IndexDef newIdx = null;

        String c = null;


        try {
            // org/brackit/server/node/index/definition/IndexDef.g:149:2: ( CREATE (newIdx= contentIndex[h] | newIdx= casIndex[h] | newIdx= pathIndex[h] | newIdx= elementIndex[h] ) (c= inContainer )? ( TERMINATOR )? )
            // org/brackit/server/node/index/definition/IndexDef.g:149:4: CREATE (newIdx= contentIndex[h] | newIdx= casIndex[h] | newIdx= pathIndex[h] | newIdx= elementIndex[h] ) (c= inContainer )? ( TERMINATOR )?
            {
            match(input,CREATE,FOLLOW_CREATE_in_indexDefinition94); if (state.failed) return result;
            // org/brackit/server/node/index/definition/IndexDef.g:150:3: (newIdx= contentIndex[h] | newIdx= casIndex[h] | newIdx= pathIndex[h] | newIdx= elementIndex[h] )
            int alt1=4;
            switch ( input.LA(1) ) {
            case CONTENT:
                {
                alt1=1;
                }
                break;
            case UNIQUE:
            case CAS:
                {
                alt1=2;
                }
                break;
            case PATH:
                {
                alt1=3;
                }
                break;
            case ELEMENT:
                {
                alt1=4;
                }
                break;
            default:
                if (state.backtracking>0) {state.failed=true; return result;}
                NoViableAltException nvae =
                    new NoViableAltException("", 1, 0, input);

                throw nvae;
            }

            switch (alt1) {
                case 1 :
                    // org/brackit/server/node/index/definition/IndexDef.g:151:4: newIdx= contentIndex[h]
                    {
                    pushFollow(FOLLOW_contentIndex_in_indexDefinition105);
                    newIdx=contentIndex(h);

                    state._fsp--;
                    if (state.failed) return result;

                    }
                    break;
                case 2 :
                    // org/brackit/server/node/index/definition/IndexDef.g:152:4: newIdx= casIndex[h]
                    {
                    pushFollow(FOLLOW_casIndex_in_indexDefinition115);
                    newIdx=casIndex(h);

                    state._fsp--;
                    if (state.failed) return result;

                    }
                    break;
                case 3 :
                    // org/brackit/server/node/index/definition/IndexDef.g:153:4: newIdx= pathIndex[h]
                    {
                    pushFollow(FOLLOW_pathIndex_in_indexDefinition125);
                    newIdx=pathIndex(h);

                    state._fsp--;
                    if (state.failed) return result;

                    }
                    break;
                case 4 :
                    // org/brackit/server/node/index/definition/IndexDef.g:154:4: newIdx= elementIndex[h]
                    {
                    pushFollow(FOLLOW_elementIndex_in_indexDefinition135);
                    newIdx=elementIndex(h);

                    state._fsp--;
                    if (state.failed) return result;

                    }
                    break;

            }

            // org/brackit/server/node/index/definition/IndexDef.g:156:4: (c= inContainer )?
            int alt2=2;
            int LA2_0 = input.LA(1);

            if ( (LA2_0==IN) ) {
                alt2=1;
            }
            switch (alt2) {
                case 1 :
                    // org/brackit/server/node/index/definition/IndexDef.g:156:4: c= inContainer
                    {
                    pushFollow(FOLLOW_inContainer_in_indexDefinition146);
                    c=inContainer();

                    state._fsp--;
                    if (state.failed) return result;

                    }
                    break;

            }

            if ( state.backtracking==0 ) {

              			result =newIdx;
              			if (c != null)
              			{
              				result.setContainerID(toInt(c));
              			}
              		
            }
            // org/brackit/server/node/index/definition/IndexDef.g:164:3: ( TERMINATOR )?
            int alt3=2;
            int LA3_0 = input.LA(1);

            if ( (LA3_0==TERMINATOR) ) {
                alt3=1;
            }
            switch (alt3) {
                case 1 :
                    // org/brackit/server/node/index/definition/IndexDef.g:164:3: TERMINATOR
                    {
                    match(input,TERMINATOR,FOLLOW_TERMINATOR_in_indexDefinition155); if (state.failed) return result;

                    }
                    break;

            }


            }

        }

        	catch (RecognitionException re)
        	{
        		// TODO: set the reported error message as the exception message
        		reportError(re);
        		throw re;
        	}
        finally {
        }
        return result;
    }
    // $ANTLR end "indexDefinition"


    // $ANTLR start "contentIndex"
    // org/brackit/server/node/index/definition/IndexDef.g:167:1: contentIndex[IndexDefHelper h] returns [IndexDef result] : CONTENT INDEX (t= typeDef )? (eaBool= forContent )? ;
    public final IndexDef contentIndex(IndexDefHelper h) throws RecognitionException {
        IndexDef result = null;

        Type t = null;

        ElementAttributeBooleans eaBool = null;



        		eaBool = new ElementAttributeBooleans(true, true);
        		Type type = Type.STR;
        	
        try {
            // org/brackit/server/node/index/definition/IndexDef.g:174:2: ( CONTENT INDEX (t= typeDef )? (eaBool= forContent )? )
            // org/brackit/server/node/index/definition/IndexDef.g:174:4: CONTENT INDEX (t= typeDef )? (eaBool= forContent )?
            {
            match(input,CONTENT,FOLLOW_CONTENT_in_contentIndex181); if (state.failed) return result;
            match(input,INDEX,FOLLOW_INDEX_in_contentIndex183); if (state.failed) return result;
            // org/brackit/server/node/index/definition/IndexDef.g:174:19: (t= typeDef )?
            int alt4=2;
            int LA4_0 = input.LA(1);

            if ( (LA4_0==OF_TYPE) ) {
                alt4=1;
            }
            switch (alt4) {
                case 1 :
                    // org/brackit/server/node/index/definition/IndexDef.g:174:19: t= typeDef
                    {
                    pushFollow(FOLLOW_typeDef_in_contentIndex187);
                    t=typeDef();

                    state._fsp--;
                    if (state.failed) return result;

                    }
                    break;

            }

            // org/brackit/server/node/index/definition/IndexDef.g:174:35: (eaBool= forContent )?
            int alt5=2;
            int LA5_0 = input.LA(1);

            if ( (LA5_0==FOR) ) {
                alt5=1;
            }
            switch (alt5) {
                case 1 :
                    // org/brackit/server/node/index/definition/IndexDef.g:174:35: eaBool= forContent
                    {
                    pushFollow(FOLLOW_forContent_in_contentIndex192);
                    eaBool=forContent();

                    state._fsp--;
                    if (state.failed) return result;

                    }
                    break;

            }

            if ( state.backtracking==0 ) {

              			type = (t != null) ? t : type;
              			result = h.createContentIndexDefinition(eaBool.element, eaBool.attribute, type);
              		
            }

            }

        }

        	catch (RecognitionException re)
        	{
        		// TODO: set the reported error message as the exception message
        		reportError(re);
        		throw re;
        	}
        finally {
        }
        return result;
    }
    // $ANTLR end "contentIndex"


    // $ANTLR start "casIndex"
    // org/brackit/server/node/index/definition/IndexDef.g:181:1: casIndex[IndexDefHelper h] returns [IndexDef result] : ( UNIQUE )? CAS INDEX pathList= pathDef (t= typeDef )? (cluster= clustering )? ;
    public final IndexDef casIndex(IndexDefHelper h) throws RecognitionException {
        IndexDef result = null;

        ArrayList<Path<String>> pathList = null;

        Type t = null;

        Cluster cluster = null;



        		boolean unique = false;
        		Type type = Type.STR;
        	
        try {
            // org/brackit/server/node/index/definition/IndexDef.g:188:2: ( ( UNIQUE )? CAS INDEX pathList= pathDef (t= typeDef )? (cluster= clustering )? )
            // org/brackit/server/node/index/definition/IndexDef.g:188:4: ( UNIQUE )? CAS INDEX pathList= pathDef (t= typeDef )? (cluster= clustering )?
            {
            // org/brackit/server/node/index/definition/IndexDef.g:188:4: ( UNIQUE )?
            int alt6=2;
            int LA6_0 = input.LA(1);

            if ( (LA6_0==UNIQUE) ) {
                alt6=1;
            }
            switch (alt6) {
                case 1 :
                    // org/brackit/server/node/index/definition/IndexDef.g:189:4: UNIQUE
                    {
                    match(input,UNIQUE,FOLLOW_UNIQUE_in_casIndex227); if (state.failed) return result;
                    if ( state.backtracking==0 ) {
                       unique = true; 
                    }

                    }
                    break;

            }

            match(input,CAS,FOLLOW_CAS_in_casIndex238); if (state.failed) return result;
            match(input,INDEX,FOLLOW_INDEX_in_casIndex240); if (state.failed) return result;
            pushFollow(FOLLOW_pathDef_in_casIndex244);
            pathList=pathDef();

            state._fsp--;
            if (state.failed) return result;
            // org/brackit/server/node/index/definition/IndexDef.g:191:31: (t= typeDef )?
            int alt7=2;
            int LA7_0 = input.LA(1);

            if ( (LA7_0==OF_TYPE) ) {
                alt7=1;
            }
            switch (alt7) {
                case 1 :
                    // org/brackit/server/node/index/definition/IndexDef.g:191:31: t= typeDef
                    {
                    pushFollow(FOLLOW_typeDef_in_casIndex248);
                    t=typeDef();

                    state._fsp--;
                    if (state.failed) return result;

                    }
                    break;

            }

            // org/brackit/server/node/index/definition/IndexDef.g:191:48: (cluster= clustering )?
            int alt8=2;
            int LA8_0 = input.LA(1);

            if ( (LA8_0==WITH) ) {
                alt8=1;
            }
            switch (alt8) {
                case 1 :
                    // org/brackit/server/node/index/definition/IndexDef.g:191:48: cluster= clustering
                    {
                    pushFollow(FOLLOW_clustering_in_casIndex253);
                    cluster=clustering();

                    state._fsp--;
                    if (state.failed) return result;

                    }
                    break;

            }

            if ( state.backtracking==0 ) {

              			type = (t != null) ? t : type;
              			result = h.createCASIndexDefinition(pathList, cluster, unique, type);
              		
            }

            }

        }

        	catch (RecognitionException re)
        	{
        		// TODO: set the reported error message as the exception message
        		reportError(re);
        		throw re;
        	}
        finally {
        }
        return result;
    }
    // $ANTLR end "casIndex"


    // $ANTLR start "pathIndex"
    // org/brackit/server/node/index/definition/IndexDef.g:198:1: pathIndex[IndexDefHelper h] returns [IndexDef result] : PATH INDEX pathList= pathDef (cluster= clustering )? ;
    public final IndexDef pathIndex(IndexDefHelper h) throws RecognitionException {
        IndexDef result = null;

        ArrayList<Path<String>> pathList = null;

        Cluster cluster = null;


        try {
            // org/brackit/server/node/index/definition/IndexDef.g:201:2: ( PATH INDEX pathList= pathDef (cluster= clustering )? )
            // org/brackit/server/node/index/definition/IndexDef.g:201:4: PATH INDEX pathList= pathDef (cluster= clustering )?
            {
            match(input,PATH,FOLLOW_PATH_in_pathIndex277); if (state.failed) return result;
            match(input,INDEX,FOLLOW_INDEX_in_pathIndex279); if (state.failed) return result;
            pushFollow(FOLLOW_pathDef_in_pathIndex283);
            pathList=pathDef();

            state._fsp--;
            if (state.failed) return result;
            // org/brackit/server/node/index/definition/IndexDef.g:201:39: (cluster= clustering )?
            int alt9=2;
            int LA9_0 = input.LA(1);

            if ( (LA9_0==WITH) ) {
                alt9=1;
            }
            switch (alt9) {
                case 1 :
                    // org/brackit/server/node/index/definition/IndexDef.g:201:39: cluster= clustering
                    {
                    pushFollow(FOLLOW_clustering_in_pathIndex287);
                    cluster=clustering();

                    state._fsp--;
                    if (state.failed) return result;

                    }
                    break;

            }

            if ( state.backtracking==0 ) {

              			result = h.createPathIndexDefinition(pathList, cluster);
              		
            }

            }

        }

        	catch (RecognitionException re)
        	{
        		// TODO: set the reported error message as the exception message
        		reportError(re);
        		throw re;
        	}
        finally {
        }
        return result;
    }
    // $ANTLR end "pathIndex"


    // $ANTLR start "elementIndex"
    // org/brackit/server/node/index/definition/IndexDef.g:212:1: elementIndex[IndexDefHelper h] returns [IndexDef result] : ELEMENT INDEX ( ( LPAREN INCLUDING )=>s= includeElementDef | f= excludeElementDef )? (cluster= clustering )? ;
    public final IndexDef elementIndex(IndexDefHelper h) throws RecognitionException {
        IndexDef result = null;

        Map<String, Cluster> s = null;

        List<String> f = null;

        Cluster cluster = null;



        		Map<String, Cluster> selection = null;
        		List<String> filter = null;
        	
        try {
            // org/brackit/server/node/index/definition/IndexDef.g:219:2: ( ELEMENT INDEX ( ( LPAREN INCLUDING )=>s= includeElementDef | f= excludeElementDef )? (cluster= clustering )? )
            // org/brackit/server/node/index/definition/IndexDef.g:219:4: ELEMENT INDEX ( ( LPAREN INCLUDING )=>s= includeElementDef | f= excludeElementDef )? (cluster= clustering )?
            {
            match(input,ELEMENT,FOLLOW_ELEMENT_in_elementIndex321); if (state.failed) return result;
            match(input,INDEX,FOLLOW_INDEX_in_elementIndex323); if (state.failed) return result;
            // org/brackit/server/node/index/definition/IndexDef.g:220:3: ( ( LPAREN INCLUDING )=>s= includeElementDef | f= excludeElementDef )?
            int alt10=3;
            int LA10_0 = input.LA(1);

            if ( (LA10_0==LPAREN) ) {
                int LA10_1 = input.LA(2);

                if ( (synpred1_IndexDef()) ) {
                    alt10=1;
                }
                else if ( (true) ) {
                    alt10=2;
                }
            }
            switch (alt10) {
                case 1 :
                    // org/brackit/server/node/index/definition/IndexDef.g:221:4: ( LPAREN INCLUDING )=>s= includeElementDef
                    {
                    pushFollow(FOLLOW_includeElementDef_in_elementIndex342);
                    s=includeElementDef();

                    state._fsp--;
                    if (state.failed) return result;
                    if ( state.backtracking==0 ) {
                       selection = s; 
                    }

                    }
                    break;
                case 2 :
                    // org/brackit/server/node/index/definition/IndexDef.g:222:4: f= excludeElementDef
                    {
                    pushFollow(FOLLOW_excludeElementDef_in_elementIndex353);
                    f=excludeElementDef();

                    state._fsp--;
                    if (state.failed) return result;
                    if ( state.backtracking==0 ) {
                       filter = f; 
                    }

                    }
                    break;

            }

            // org/brackit/server/node/index/definition/IndexDef.g:224:10: (cluster= clustering )?
            int alt11=2;
            int LA11_0 = input.LA(1);

            if ( (LA11_0==WITH) ) {
                alt11=1;
            }
            switch (alt11) {
                case 1 :
                    // org/brackit/server/node/index/definition/IndexDef.g:224:10: cluster= clustering
                    {
                    pushFollow(FOLLOW_clustering_in_elementIndex366);
                    cluster=clustering();

                    state._fsp--;
                    if (state.failed) return result;

                    }
                    break;

            }

            if ( state.backtracking==0 ) {

              			result = h.createElementIndexDefinition(selection, filter, cluster);
              		
            }

            }

        }

        	catch (RecognitionException re)
        	{
        		// TODO: set the reported error message as the exception message
        		reportError(re);
        		throw re;
        	}
        finally {
        }
        return result;
    }
    // $ANTLR end "elementIndex"


    // $ANTLR start "includeElementDef"
    // org/brackit/server/node/index/definition/IndexDef.g:230:1: includeElementDef returns [Map<String, Cluster> result] : LPAREN INCLUDING qnameWithCluster[$result] ( COMMA qnameWithCluster[$result] )* RPAREN ;
    public final Map<String, Cluster> includeElementDef() throws RecognitionException {
        Map<String, Cluster> result = null;


        		result = new HashMap<String, Cluster>();
        	
        try {
            // org/brackit/server/node/index/definition/IndexDef.g:235:2: ( LPAREN INCLUDING qnameWithCluster[$result] ( COMMA qnameWithCluster[$result] )* RPAREN )
            // org/brackit/server/node/index/definition/IndexDef.g:235:4: LPAREN INCLUDING qnameWithCluster[$result] ( COMMA qnameWithCluster[$result] )* RPAREN
            {
            match(input,LPAREN,FOLLOW_LPAREN_in_includeElementDef393); if (state.failed) return result;
            match(input,INCLUDING,FOLLOW_INCLUDING_in_includeElementDef395); if (state.failed) return result;
            pushFollow(FOLLOW_qnameWithCluster_in_includeElementDef399);
            qnameWithCluster(result);

            state._fsp--;
            if (state.failed) return result;
            // org/brackit/server/node/index/definition/IndexDef.g:237:3: ( COMMA qnameWithCluster[$result] )*
            loop12:
            do {
                int alt12=2;
                int LA12_0 = input.LA(1);

                if ( (LA12_0==COMMA) ) {
                    alt12=1;
                }


                switch (alt12) {
            	case 1 :
            	    // org/brackit/server/node/index/definition/IndexDef.g:237:4: COMMA qnameWithCluster[$result]
            	    {
            	    match(input,COMMA,FOLLOW_COMMA_in_includeElementDef405); if (state.failed) return result;
            	    pushFollow(FOLLOW_qnameWithCluster_in_includeElementDef407);
            	    qnameWithCluster(result);

            	    state._fsp--;
            	    if (state.failed) return result;

            	    }
            	    break;

            	default :
            	    break loop12;
                }
            } while (true);

            match(input,RPAREN,FOLLOW_RPAREN_in_includeElementDef412); if (state.failed) return result;

            }

        }

        	catch (RecognitionException re)
        	{
        		// TODO: set the reported error message as the exception message
        		reportError(re);
        		throw re;
        	}
        finally {
        }
        return result;
    }
    // $ANTLR end "includeElementDef"


    // $ANTLR start "excludeElementDef"
    // org/brackit/server/node/index/definition/IndexDef.g:240:1: excludeElementDef returns [List<String> result] : LPAREN EXCLUDING q= qname ( COMMA q= qname )* RPAREN ;
    public final List<String> excludeElementDef() throws RecognitionException {
        List<String> result = null;

        IndexDefParser.qname_return q = null;



        		result = new ArrayList<String>();
        	
        try {
            // org/brackit/server/node/index/definition/IndexDef.g:245:2: ( LPAREN EXCLUDING q= qname ( COMMA q= qname )* RPAREN )
            // org/brackit/server/node/index/definition/IndexDef.g:245:4: LPAREN EXCLUDING q= qname ( COMMA q= qname )* RPAREN
            {
            match(input,LPAREN,FOLLOW_LPAREN_in_excludeElementDef434); if (state.failed) return result;
            match(input,EXCLUDING,FOLLOW_EXCLUDING_in_excludeElementDef436); if (state.failed) return result;
            pushFollow(FOLLOW_qname_in_excludeElementDef442);
            q=qname();

            state._fsp--;
            if (state.failed) return result;
            if ( state.backtracking==0 ) {
               result.add((q!=null?input.toString(q.start,q.stop):null)); 
            }
            // org/brackit/server/node/index/definition/IndexDef.g:247:3: ( COMMA q= qname )*
            loop13:
            do {
                int alt13=2;
                int LA13_0 = input.LA(1);

                if ( (LA13_0==COMMA) ) {
                    alt13=1;
                }


                switch (alt13) {
            	case 1 :
            	    // org/brackit/server/node/index/definition/IndexDef.g:248:4: COMMA q= qname
            	    {
            	    match(input,COMMA,FOLLOW_COMMA_in_excludeElementDef453); if (state.failed) return result;
            	    pushFollow(FOLLOW_qname_in_excludeElementDef460);
            	    q=qname();

            	    state._fsp--;
            	    if (state.failed) return result;
            	    if ( state.backtracking==0 ) {
            	       result.add((q!=null?input.toString(q.start,q.stop):null)); 
            	    }

            	    }
            	    break;

            	default :
            	    break loop13;
                }
            } while (true);

            match(input,RPAREN,FOLLOW_RPAREN_in_excludeElementDef471); if (state.failed) return result;

            }

        }

        	catch (RecognitionException re)
        	{
        		// TODO: set the reported error message as the exception message
        		reportError(re);
        		throw re;
        	}
        finally {
        }
        return result;
    }
    // $ANTLR end "excludeElementDef"


    // $ANTLR start "qnameWithCluster"
    // org/brackit/server/node/index/definition/IndexDef.g:254:1: qnameWithCluster[Map<String, Cluster> map] : q= qname ( AT ( SPLID | PCR ) )? ;
    public final void qnameWithCluster(Map<String, Cluster> map) throws RecognitionException {
        IndexDefParser.qname_return q = null;



        		Cluster cluster = null;
        	
        try {
            // org/brackit/server/node/index/definition/IndexDef.g:259:2: (q= qname ( AT ( SPLID | PCR ) )? )
            // org/brackit/server/node/index/definition/IndexDef.g:259:4: q= qname ( AT ( SPLID | PCR ) )?
            {
            pushFollow(FOLLOW_qname_in_qnameWithCluster493);
            q=qname();

            state._fsp--;
            if (state.failed) return ;
            // org/brackit/server/node/index/definition/IndexDef.g:260:3: ( AT ( SPLID | PCR ) )?
            int alt15=2;
            int LA15_0 = input.LA(1);

            if ( (LA15_0==AT) ) {
                alt15=1;
            }
            switch (alt15) {
                case 1 :
                    // org/brackit/server/node/index/definition/IndexDef.g:260:4: AT ( SPLID | PCR )
                    {
                    match(input,AT,FOLLOW_AT_in_qnameWithCluster498); if (state.failed) return ;
                    // org/brackit/server/node/index/definition/IndexDef.g:260:7: ( SPLID | PCR )
                    int alt14=2;
                    int LA14_0 = input.LA(1);

                    if ( (LA14_0==SPLID) ) {
                        alt14=1;
                    }
                    else if ( (LA14_0==PCR) ) {
                        alt14=2;
                    }
                    else {
                        if (state.backtracking>0) {state.failed=true; return ;}
                        NoViableAltException nvae =
                            new NoViableAltException("", 14, 0, input);

                        throw nvae;
                    }
                    switch (alt14) {
                        case 1 :
                            // org/brackit/server/node/index/definition/IndexDef.g:261:4: SPLID
                            {
                            match(input,SPLID,FOLLOW_SPLID_in_qnameWithCluster505); if (state.failed) return ;
                            if ( state.backtracking==0 ) {
                               cluster = Cluster.SPLID; 
                            }

                            }
                            break;
                        case 2 :
                            // org/brackit/server/node/index/definition/IndexDef.g:262:4: PCR
                            {
                            match(input,PCR,FOLLOW_PCR_in_qnameWithCluster514); if (state.failed) return ;
                            if ( state.backtracking==0 ) {
                               cluster = Cluster.PCR; 
                            }

                            }
                            break;

                    }


                    }
                    break;

            }

            if ( state.backtracking==0 ) {

              			map.put((q!=null?input.toString(q.start,q.stop):null), cluster);
              		
            }

            }

        }

        	catch (RecognitionException re)
        	{
        		// TODO: set the reported error message as the exception message
        		reportError(re);
        		throw re;
        	}
        finally {
        }
        return ;
    }
    // $ANTLR end "qnameWithCluster"


    // $ANTLR start "forContent"
    // org/brackit/server/node/index/definition/IndexDef.g:269:1: forContent returns [ElementAttributeBooleans result] : FOR ( ELEMENT | ATTRIBUTE | ALL ) CONTENT ;
    public final ElementAttributeBooleans forContent() throws RecognitionException {
        ElementAttributeBooleans result = null;

        try {
            // org/brackit/server/node/index/definition/IndexDef.g:271:2: ( FOR ( ELEMENT | ATTRIBUTE | ALL ) CONTENT )
            // org/brackit/server/node/index/definition/IndexDef.g:271:4: FOR ( ELEMENT | ATTRIBUTE | ALL ) CONTENT
            {
            match(input,FOR,FOLLOW_FOR_in_forContent542); if (state.failed) return result;
            // org/brackit/server/node/index/definition/IndexDef.g:272:3: ( ELEMENT | ATTRIBUTE | ALL )
            int alt16=3;
            switch ( input.LA(1) ) {
            case ELEMENT:
                {
                alt16=1;
                }
                break;
            case ATTRIBUTE:
                {
                alt16=2;
                }
                break;
            case ALL:
                {
                alt16=3;
                }
                break;
            default:
                if (state.backtracking>0) {state.failed=true; return result;}
                NoViableAltException nvae =
                    new NoViableAltException("", 16, 0, input);

                throw nvae;
            }

            switch (alt16) {
                case 1 :
                    // org/brackit/server/node/index/definition/IndexDef.g:273:4: ELEMENT
                    {
                    match(input,ELEMENT,FOLLOW_ELEMENT_in_forContent551); if (state.failed) return result;
                    if ( state.backtracking==0 ) {
                       result = new ElementAttributeBooleans(true, false); 
                    }

                    }
                    break;
                case 2 :
                    // org/brackit/server/node/index/definition/IndexDef.g:274:4: ATTRIBUTE
                    {
                    match(input,ATTRIBUTE,FOLLOW_ATTRIBUTE_in_forContent560); if (state.failed) return result;
                    if ( state.backtracking==0 ) {
                       result = new ElementAttributeBooleans(false, true); 
                    }

                    }
                    break;
                case 3 :
                    // org/brackit/server/node/index/definition/IndexDef.g:275:4: ALL
                    {
                    match(input,ALL,FOLLOW_ALL_in_forContent570); if (state.failed) return result;
                    if ( state.backtracking==0 ) {
                       result = new ElementAttributeBooleans(true, true); 
                    }

                    }
                    break;

            }

            match(input,CONTENT,FOLLOW_CONTENT_in_forContent581); if (state.failed) return result;

            }

        }

        	catch (RecognitionException re)
        	{
        		// TODO: set the reported error message as the exception message
        		reportError(re);
        		throw re;
        	}
        finally {
        }
        return result;
    }
    // $ANTLR end "forContent"


    // $ANTLR start "clustering"
    // org/brackit/server/node/index/definition/IndexDef.g:280:1: clustering returns [Cluster result] : WITH ( SPLID | PCR ) CLUSTERING ;
    public final Cluster clustering() throws RecognitionException {
        Cluster result = null;

        try {
            // org/brackit/server/node/index/definition/IndexDef.g:282:2: ( WITH ( SPLID | PCR ) CLUSTERING )
            // org/brackit/server/node/index/definition/IndexDef.g:282:4: WITH ( SPLID | PCR ) CLUSTERING
            {
            match(input,WITH,FOLLOW_WITH_in_clustering597); if (state.failed) return result;
            // org/brackit/server/node/index/definition/IndexDef.g:283:3: ( SPLID | PCR )
            int alt17=2;
            int LA17_0 = input.LA(1);

            if ( (LA17_0==SPLID) ) {
                alt17=1;
            }
            else if ( (LA17_0==PCR) ) {
                alt17=2;
            }
            else {
                if (state.backtracking>0) {state.failed=true; return result;}
                NoViableAltException nvae =
                    new NoViableAltException("", 17, 0, input);

                throw nvae;
            }
            switch (alt17) {
                case 1 :
                    // org/brackit/server/node/index/definition/IndexDef.g:284:4: SPLID
                    {
                    match(input,SPLID,FOLLOW_SPLID_in_clustering606); if (state.failed) return result;
                    if ( state.backtracking==0 ) {
                       result = Cluster.SPLID; 
                    }

                    }
                    break;
                case 2 :
                    // org/brackit/server/node/index/definition/IndexDef.g:285:4: PCR
                    {
                    match(input,PCR,FOLLOW_PCR_in_clustering615); if (state.failed) return result;
                    if ( state.backtracking==0 ) {
                       result = Cluster.PCR; 
                    }

                    }
                    break;

            }

            match(input,CLUSTERING,FOLLOW_CLUSTERING_in_clustering625); if (state.failed) return result;

            }

        }

        	catch (RecognitionException re)
        	{
        		// TODO: set the reported error message as the exception message
        		reportError(re);
        		throw re;
        	}
        finally {
        }
        return result;
    }
    // $ANTLR end "clustering"


    // $ANTLR start "typeDef"
    // org/brackit/server/node/index/definition/IndexDef.g:290:1: typeDef returns [Type result] : OF_TYPE t= type ;
    public final Type typeDef() throws RecognitionException {
        Type result = null;

        Type t = null;


        try {
            // org/brackit/server/node/index/definition/IndexDef.g:292:2: ( OF_TYPE t= type )
            // org/brackit/server/node/index/definition/IndexDef.g:292:4: OF_TYPE t= type
            {
            match(input,OF_TYPE,FOLLOW_OF_TYPE_in_typeDef641); if (state.failed) return result;
            pushFollow(FOLLOW_type_in_typeDef645);
            t=type();

            state._fsp--;
            if (state.failed) return result;
            if ( state.backtracking==0 ) {
               result = t; 
            }

            }

        }

        	catch (RecognitionException re)
        	{
        		// TODO: set the reported error message as the exception message
        		reportError(re);
        		throw re;
        	}
        finally {
        }
        return result;
    }
    // $ANTLR end "typeDef"


    // $ANTLR start "type"
    // org/brackit/server/node/index/definition/IndexDef.g:294:1: type returns [ Type t] : ( STRING | DOUBLE | LONG | INTEGER );
    public final Type type() throws RecognitionException {
        Type t = null;

        try {
            // org/brackit/server/node/index/definition/IndexDef.g:296:2: ( STRING | DOUBLE | LONG | INTEGER )
            int alt18=4;
            switch ( input.LA(1) ) {
            case STRING:
                {
                alt18=1;
                }
                break;
            case DOUBLE:
                {
                alt18=2;
                }
                break;
            case LONG:
                {
                alt18=3;
                }
                break;
            case INTEGER:
                {
                alt18=4;
                }
                break;
            default:
                if (state.backtracking>0) {state.failed=true; return t;}
                NoViableAltException nvae =
                    new NoViableAltException("", 18, 0, input);

                throw nvae;
            }

            switch (alt18) {
                case 1 :
                    // org/brackit/server/node/index/definition/IndexDef.g:297:2: STRING
                    {
                    match(input,STRING,FOLLOW_STRING_in_type663); if (state.failed) return t;
                    if ( state.backtracking==0 ) {
                       t = Type.STR; 
                    }

                    }
                    break;
                case 2 :
                    // org/brackit/server/node/index/definition/IndexDef.g:298:4: DOUBLE
                    {
                    match(input,DOUBLE,FOLLOW_DOUBLE_in_type670); if (state.failed) return t;
                    if ( state.backtracking==0 ) {
                       t = Type.DBL; 
                    }

                    }
                    break;
                case 3 :
                    // org/brackit/server/node/index/definition/IndexDef.g:299:4: LONG
                    {
                    match(input,LONG,FOLLOW_LONG_in_type677); if (state.failed) return t;
                    if ( state.backtracking==0 ) {
                       t = Type.LON; 
                    }

                    }
                    break;
                case 4 :
                    // org/brackit/server/node/index/definition/IndexDef.g:300:4: INTEGER
                    {
                    match(input,INTEGER,FOLLOW_INTEGER_in_type684); if (state.failed) return t;
                    if ( state.backtracking==0 ) {
                       t = Type.INR; 
                    }

                    }
                    break;

            }
        }

        	catch (RecognitionException re)
        	{
        		// TODO: set the reported error message as the exception message
        		reportError(re);
        		throw re;
        	}
        finally {
        }
        return t;
    }
    // $ANTLR end "type"


    // $ANTLR start "pathDef"
    // org/brackit/server/node/index/definition/IndexDef.g:303:1: pathDef returns [ArrayList<Path<String>> result] : ( PATHS | ON ) p= path ( COMMA p= path )* ;
    public final ArrayList<Path<String>> pathDef() throws RecognitionException {
        ArrayList<Path<String>> result = null;

        Path<String> p = null;



        		result = new ArrayList<Path<String>>();
        	
        try {
            // org/brackit/server/node/index/definition/IndexDef.g:308:2: ( ( PATHS | ON ) p= path ( COMMA p= path )* )
            // org/brackit/server/node/index/definition/IndexDef.g:308:4: ( PATHS | ON ) p= path ( COMMA p= path )*
            {
            if ( (input.LA(1)>=PATHS && input.LA(1)<=ON) ) {
                input.consume();
                state.errorRecovery=false;state.failed=false;
            }
            else {
                if (state.backtracking>0) {state.failed=true; return result;}
                MismatchedSetException mse = new MismatchedSetException(null,input);
                throw mse;
            }

            pushFollow(FOLLOW_path_in_pathDef720);
            p=path();

            state._fsp--;
            if (state.failed) return result;
            if ( state.backtracking==0 ) {
               result.add(p); 
            }
            // org/brackit/server/node/index/definition/IndexDef.g:310:3: ( COMMA p= path )*
            loop19:
            do {
                int alt19=2;
                int LA19_0 = input.LA(1);

                if ( (LA19_0==COMMA) ) {
                    alt19=1;
                }


                switch (alt19) {
            	case 1 :
            	    // org/brackit/server/node/index/definition/IndexDef.g:311:4: COMMA p= path
            	    {
            	    match(input,COMMA,FOLLOW_COMMA_in_pathDef731); if (state.failed) return result;
            	    pushFollow(FOLLOW_path_in_pathDef738);
            	    p=path();

            	    state._fsp--;
            	    if (state.failed) return result;
            	    if ( state.backtracking==0 ) {
            	       result.add(p); 
            	    }

            	    }
            	    break;

            	default :
            	    break loop19;
                }
            } while (true);


            }

        }

        	catch (RecognitionException re)
        	{
        		// TODO: set the reported error message as the exception message
        		reportError(re);
        		throw re;
        	}
        finally {
        }
        return result;
    }
    // $ANTLR end "pathDef"


    // $ANTLR start "path"
    // org/brackit/server/node/index/definition/IndexDef.g:317:1: path returns [ Path<String> p ] : ( namedstep[p] )* ( attributestep[p] )? ;
    public final Path<String> path() throws RecognitionException {
        Path<String> p = null;

        try {
            // org/brackit/server/node/index/definition/IndexDef.g:317:32: ( ( namedstep[p] )* ( attributestep[p] )? )
            // org/brackit/server/node/index/definition/IndexDef.g:318:2: ( namedstep[p] )* ( attributestep[p] )?
            {
            if ( state.backtracking==0 ) {
               p = new Path<String>(); 
            }
            // org/brackit/server/node/index/definition/IndexDef.g:319:2: ( namedstep[p] )*
            loop20:
            do {
                int alt20=2;
                int LA20_0 = input.LA(1);

                if ( ((LA20_0>=CHILD && LA20_0<=DESC)) ) {
                    alt20=1;
                }


                switch (alt20) {
            	case 1 :
            	    // org/brackit/server/node/index/definition/IndexDef.g:319:2: namedstep[p]
            	    {
            	    pushFollow(FOLLOW_namedstep_in_path764);
            	    namedstep(p);

            	    state._fsp--;
            	    if (state.failed) return p;

            	    }
            	    break;

            	default :
            	    break loop20;
                }
            } while (true);

            // org/brackit/server/node/index/definition/IndexDef.g:319:16: ( attributestep[p] )?
            int alt21=2;
            int LA21_0 = input.LA(1);

            if ( (LA21_0==CHILD_ATT||LA21_0==DESC_ATT) ) {
                alt21=1;
            }
            switch (alt21) {
                case 1 :
                    // org/brackit/server/node/index/definition/IndexDef.g:319:16: attributestep[p]
                    {
                    pushFollow(FOLLOW_attributestep_in_path768);
                    attributestep(p);

                    state._fsp--;
                    if (state.failed) return p;

                    }
                    break;

            }

            if ( state.backtracking==0 ) {
               /* System.out.println("path: " + p); */ 
            }

            }

        }

        	catch (RecognitionException re)
        	{
        		// TODO: set the reported error message as the exception message
        		reportError(re);
        		throw re;
        	}
        finally {
        }
        return p;
    }
    // $ANTLR end "path"


    // $ANTLR start "attributestep"
    // org/brackit/server/node/index/definition/IndexDef.g:322:1: attributestep[ Path<String> p] : ( ( CHILD_ATT ( ( WILDCARD ) | (s= NCNAME ) ) ) | ( DESC_ATT ( ( WILDCARD ) | (s= NCNAME ) ) ) ) ;
    public final void attributestep(Path<String> p) throws RecognitionException {
        Token s=null;


        		String tag = null;
        	
        try {
            // org/brackit/server/node/index/definition/IndexDef.g:326:2: ( ( ( CHILD_ATT ( ( WILDCARD ) | (s= NCNAME ) ) ) | ( DESC_ATT ( ( WILDCARD ) | (s= NCNAME ) ) ) ) )
            // org/brackit/server/node/index/definition/IndexDef.g:327:2: ( ( CHILD_ATT ( ( WILDCARD ) | (s= NCNAME ) ) ) | ( DESC_ATT ( ( WILDCARD ) | (s= NCNAME ) ) ) )
            {
            // org/brackit/server/node/index/definition/IndexDef.g:327:2: ( ( CHILD_ATT ( ( WILDCARD ) | (s= NCNAME ) ) ) | ( DESC_ATT ( ( WILDCARD ) | (s= NCNAME ) ) ) )
            int alt24=2;
            int LA24_0 = input.LA(1);

            if ( (LA24_0==CHILD_ATT) ) {
                alt24=1;
            }
            else if ( (LA24_0==DESC_ATT) ) {
                alt24=2;
            }
            else {
                if (state.backtracking>0) {state.failed=true; return ;}
                NoViableAltException nvae =
                    new NoViableAltException("", 24, 0, input);

                throw nvae;
            }
            switch (alt24) {
                case 1 :
                    // org/brackit/server/node/index/definition/IndexDef.g:327:3: ( CHILD_ATT ( ( WILDCARD ) | (s= NCNAME ) ) )
                    {
                    // org/brackit/server/node/index/definition/IndexDef.g:327:3: ( CHILD_ATT ( ( WILDCARD ) | (s= NCNAME ) ) )
                    // org/brackit/server/node/index/definition/IndexDef.g:327:4: CHILD_ATT ( ( WILDCARD ) | (s= NCNAME ) )
                    {
                    match(input,CHILD_ATT,FOLLOW_CHILD_ATT_in_attributestep794); if (state.failed) return ;
                    // org/brackit/server/node/index/definition/IndexDef.g:327:14: ( ( WILDCARD ) | (s= NCNAME ) )
                    int alt22=2;
                    int LA22_0 = input.LA(1);

                    if ( (LA22_0==WILDCARD) ) {
                        alt22=1;
                    }
                    else if ( (LA22_0==NCNAME) ) {
                        alt22=2;
                    }
                    else {
                        if (state.backtracking>0) {state.failed=true; return ;}
                        NoViableAltException nvae =
                            new NoViableAltException("", 22, 0, input);

                        throw nvae;
                    }
                    switch (alt22) {
                        case 1 :
                            // org/brackit/server/node/index/definition/IndexDef.g:327:15: ( WILDCARD )
                            {
                            // org/brackit/server/node/index/definition/IndexDef.g:327:15: ( WILDCARD )
                            // org/brackit/server/node/index/definition/IndexDef.g:327:16: WILDCARD
                            {
                            match(input,WILDCARD,FOLLOW_WILDCARD_in_attributestep798); if (state.failed) return ;

                            }


                            }
                            break;
                        case 2 :
                            // org/brackit/server/node/index/definition/IndexDef.g:327:28: (s= NCNAME )
                            {
                            // org/brackit/server/node/index/definition/IndexDef.g:327:28: (s= NCNAME )
                            // org/brackit/server/node/index/definition/IndexDef.g:327:29: s= NCNAME
                            {
                            s=(Token)match(input,NCNAME,FOLLOW_NCNAME_in_attributestep806); if (state.failed) return ;
                            if ( state.backtracking==0 ) {
                              tag = (s!=null?s.getText():null);
                            }

                            }


                            }
                            break;

                    }

                    if ( state.backtracking==0 ) {
                       p.attribute(tag);
                    }

                    }


                    }
                    break;
                case 2 :
                    // org/brackit/server/node/index/definition/IndexDef.g:328:4: ( DESC_ATT ( ( WILDCARD ) | (s= NCNAME ) ) )
                    {
                    // org/brackit/server/node/index/definition/IndexDef.g:328:4: ( DESC_ATT ( ( WILDCARD ) | (s= NCNAME ) ) )
                    // org/brackit/server/node/index/definition/IndexDef.g:328:5: DESC_ATT ( ( WILDCARD ) | (s= NCNAME ) )
                    {
                    match(input,DESC_ATT,FOLLOW_DESC_ATT_in_attributestep819); if (state.failed) return ;
                    // org/brackit/server/node/index/definition/IndexDef.g:328:14: ( ( WILDCARD ) | (s= NCNAME ) )
                    int alt23=2;
                    int LA23_0 = input.LA(1);

                    if ( (LA23_0==WILDCARD) ) {
                        alt23=1;
                    }
                    else if ( (LA23_0==NCNAME) ) {
                        alt23=2;
                    }
                    else {
                        if (state.backtracking>0) {state.failed=true; return ;}
                        NoViableAltException nvae =
                            new NoViableAltException("", 23, 0, input);

                        throw nvae;
                    }
                    switch (alt23) {
                        case 1 :
                            // org/brackit/server/node/index/definition/IndexDef.g:328:15: ( WILDCARD )
                            {
                            // org/brackit/server/node/index/definition/IndexDef.g:328:15: ( WILDCARD )
                            // org/brackit/server/node/index/definition/IndexDef.g:328:16: WILDCARD
                            {
                            match(input,WILDCARD,FOLLOW_WILDCARD_in_attributestep823); if (state.failed) return ;

                            }


                            }
                            break;
                        case 2 :
                            // org/brackit/server/node/index/definition/IndexDef.g:328:28: (s= NCNAME )
                            {
                            // org/brackit/server/node/index/definition/IndexDef.g:328:28: (s= NCNAME )
                            // org/brackit/server/node/index/definition/IndexDef.g:328:29: s= NCNAME
                            {
                            s=(Token)match(input,NCNAME,FOLLOW_NCNAME_in_attributestep831); if (state.failed) return ;
                            if ( state.backtracking==0 ) {
                              tag = (s!=null?s.getText():null);
                            }

                            }


                            }
                            break;

                    }

                    if ( state.backtracking==0 ) {
                       p.descendantAttribute(tag);
                    }

                    }


                    }
                    break;

            }


            }

        }

        	catch (RecognitionException re)
        	{
        		// TODO: set the reported error message as the exception message
        		reportError(re);
        		throw re;
        	}
        finally {
        }
        return ;
    }
    // $ANTLR end "attributestep"


    // $ANTLR start "namedstep"
    // org/brackit/server/node/index/definition/IndexDef.g:331:1: namedstep[ Path<String> p] : ( ( CHILD ( ( WILDCARD ) | (s= NCNAME ) ) ) | ( DESC ( ( WILDCARD ) | (s= NCNAME ) ) ) ) ;
    public final void namedstep(Path<String> p) throws RecognitionException {
        Token s=null;


        		String tag = null;
        	
        try {
            // org/brackit/server/node/index/definition/IndexDef.g:335:2: ( ( ( CHILD ( ( WILDCARD ) | (s= NCNAME ) ) ) | ( DESC ( ( WILDCARD ) | (s= NCNAME ) ) ) ) )
            // org/brackit/server/node/index/definition/IndexDef.g:336:2: ( ( CHILD ( ( WILDCARD ) | (s= NCNAME ) ) ) | ( DESC ( ( WILDCARD ) | (s= NCNAME ) ) ) )
            {
            // org/brackit/server/node/index/definition/IndexDef.g:336:2: ( ( CHILD ( ( WILDCARD ) | (s= NCNAME ) ) ) | ( DESC ( ( WILDCARD ) | (s= NCNAME ) ) ) )
            int alt27=2;
            int LA27_0 = input.LA(1);

            if ( (LA27_0==CHILD) ) {
                alt27=1;
            }
            else if ( (LA27_0==DESC) ) {
                alt27=2;
            }
            else {
                if (state.backtracking>0) {state.failed=true; return ;}
                NoViableAltException nvae =
                    new NoViableAltException("", 27, 0, input);

                throw nvae;
            }
            switch (alt27) {
                case 1 :
                    // org/brackit/server/node/index/definition/IndexDef.g:336:3: ( CHILD ( ( WILDCARD ) | (s= NCNAME ) ) )
                    {
                    // org/brackit/server/node/index/definition/IndexDef.g:336:3: ( CHILD ( ( WILDCARD ) | (s= NCNAME ) ) )
                    // org/brackit/server/node/index/definition/IndexDef.g:336:4: CHILD ( ( WILDCARD ) | (s= NCNAME ) )
                    {
                    match(input,CHILD,FOLLOW_CHILD_in_namedstep860); if (state.failed) return ;
                    // org/brackit/server/node/index/definition/IndexDef.g:336:10: ( ( WILDCARD ) | (s= NCNAME ) )
                    int alt25=2;
                    int LA25_0 = input.LA(1);

                    if ( (LA25_0==WILDCARD) ) {
                        alt25=1;
                    }
                    else if ( (LA25_0==NCNAME) ) {
                        alt25=2;
                    }
                    else {
                        if (state.backtracking>0) {state.failed=true; return ;}
                        NoViableAltException nvae =
                            new NoViableAltException("", 25, 0, input);

                        throw nvae;
                    }
                    switch (alt25) {
                        case 1 :
                            // org/brackit/server/node/index/definition/IndexDef.g:336:11: ( WILDCARD )
                            {
                            // org/brackit/server/node/index/definition/IndexDef.g:336:11: ( WILDCARD )
                            // org/brackit/server/node/index/definition/IndexDef.g:336:12: WILDCARD
                            {
                            match(input,WILDCARD,FOLLOW_WILDCARD_in_namedstep864); if (state.failed) return ;

                            }


                            }
                            break;
                        case 2 :
                            // org/brackit/server/node/index/definition/IndexDef.g:336:24: (s= NCNAME )
                            {
                            // org/brackit/server/node/index/definition/IndexDef.g:336:24: (s= NCNAME )
                            // org/brackit/server/node/index/definition/IndexDef.g:336:25: s= NCNAME
                            {
                            s=(Token)match(input,NCNAME,FOLLOW_NCNAME_in_namedstep872); if (state.failed) return ;
                            if ( state.backtracking==0 ) {
                              tag = (s!=null?s.getText():null);
                            }

                            }


                            }
                            break;

                    }

                    if ( state.backtracking==0 ) {
                       p.child(tag);
                    }

                    }


                    }
                    break;
                case 2 :
                    // org/brackit/server/node/index/definition/IndexDef.g:337:4: ( DESC ( ( WILDCARD ) | (s= NCNAME ) ) )
                    {
                    // org/brackit/server/node/index/definition/IndexDef.g:337:4: ( DESC ( ( WILDCARD ) | (s= NCNAME ) ) )
                    // org/brackit/server/node/index/definition/IndexDef.g:337:5: DESC ( ( WILDCARD ) | (s= NCNAME ) )
                    {
                    match(input,DESC,FOLLOW_DESC_in_namedstep885); if (state.failed) return ;
                    // org/brackit/server/node/index/definition/IndexDef.g:337:10: ( ( WILDCARD ) | (s= NCNAME ) )
                    int alt26=2;
                    int LA26_0 = input.LA(1);

                    if ( (LA26_0==WILDCARD) ) {
                        alt26=1;
                    }
                    else if ( (LA26_0==NCNAME) ) {
                        alt26=2;
                    }
                    else {
                        if (state.backtracking>0) {state.failed=true; return ;}
                        NoViableAltException nvae =
                            new NoViableAltException("", 26, 0, input);

                        throw nvae;
                    }
                    switch (alt26) {
                        case 1 :
                            // org/brackit/server/node/index/definition/IndexDef.g:337:11: ( WILDCARD )
                            {
                            // org/brackit/server/node/index/definition/IndexDef.g:337:11: ( WILDCARD )
                            // org/brackit/server/node/index/definition/IndexDef.g:337:12: WILDCARD
                            {
                            match(input,WILDCARD,FOLLOW_WILDCARD_in_namedstep889); if (state.failed) return ;

                            }


                            }
                            break;
                        case 2 :
                            // org/brackit/server/node/index/definition/IndexDef.g:337:24: (s= NCNAME )
                            {
                            // org/brackit/server/node/index/definition/IndexDef.g:337:24: (s= NCNAME )
                            // org/brackit/server/node/index/definition/IndexDef.g:337:25: s= NCNAME
                            {
                            s=(Token)match(input,NCNAME,FOLLOW_NCNAME_in_namedstep897); if (state.failed) return ;
                            if ( state.backtracking==0 ) {
                              tag = (s!=null?s.getText():null);
                            }

                            }


                            }
                            break;

                    }

                    if ( state.backtracking==0 ) {
                       p.descendant(tag);
                    }

                    }


                    }
                    break;

            }


            }

        }

        	catch (RecognitionException re)
        	{
        		// TODO: set the reported error message as the exception message
        		reportError(re);
        		throw re;
        	}
        finally {
        }
        return ;
    }
    // $ANTLR end "namedstep"


    // $ANTLR start "inContainer"
    // org/brackit/server/node/index/definition/IndexDef.g:340:1: inContainer returns [String result] : IN no= cntNo ;
    public final String inContainer() throws RecognitionException {
        String result = null;

        IndexDefParser.cntNo_return no = null;


        try {
            // org/brackit/server/node/index/definition/IndexDef.g:342:2: ( IN no= cntNo )
            // org/brackit/server/node/index/definition/IndexDef.g:342:4: IN no= cntNo
            {
            match(input,IN,FOLLOW_IN_in_inContainer921); if (state.failed) return result;
            pushFollow(FOLLOW_cntNo_in_inContainer925);
            no=cntNo();

            state._fsp--;
            if (state.failed) return result;
            if ( state.backtracking==0 ) {
               result =(no!=null?input.toString(no.start,no.stop):null); 
            }

            }

        }

        	catch (RecognitionException re)
        	{
        		// TODO: set the reported error message as the exception message
        		reportError(re);
        		throw re;
        	}
        finally {
        }
        return result;
    }
    // $ANTLR end "inContainer"

    public static class cntNo_return extends ParserRuleReturnScope {
    };

    // $ANTLR start "cntNo"
    // org/brackit/server/node/index/definition/IndexDef.g:345:1: cntNo : DIGITS ;
    public final IndexDefParser.cntNo_return cntNo() throws RecognitionException {
        IndexDefParser.cntNo_return retval = new IndexDefParser.cntNo_return();
        retval.start = input.LT(1);

        try {
            // org/brackit/server/node/index/definition/IndexDef.g:346:2: ( DIGITS )
            // org/brackit/server/node/index/definition/IndexDef.g:346:4: DIGITS
            {
            match(input,DIGITS,FOLLOW_DIGITS_in_cntNo939); if (state.failed) return retval;

            }

            retval.stop = input.LT(-1);

        }

        	catch (RecognitionException re)
        	{
        		// TODO: set the reported error message as the exception message
        		reportError(re);
        		throw re;
        	}
        finally {
        }
        return retval;
    }
    // $ANTLR end "cntNo"

    public static class qname_return extends ParserRuleReturnScope {
        public String result;
    };

    // $ANTLR start "qname"
    // org/brackit/server/node/index/definition/IndexDef.g:349:1: qname returns [String result] : ( ( NCNAME COLON )=>prefix= NCNAME COLON local= NCNAME | fullName= NCNAME );
    public final IndexDefParser.qname_return qname() throws RecognitionException {
        IndexDefParser.qname_return retval = new IndexDefParser.qname_return();
        retval.start = input.LT(1);

        Token prefix=null;
        Token local=null;
        Token fullName=null;

        try {
            // org/brackit/server/node/index/definition/IndexDef.g:351:3: ( ( NCNAME COLON )=>prefix= NCNAME COLON local= NCNAME | fullName= NCNAME )
            int alt28=2;
            int LA28_0 = input.LA(1);

            if ( (LA28_0==NCNAME) ) {
                int LA28_1 = input.LA(2);

                if ( (synpred2_IndexDef()) ) {
                    alt28=1;
                }
                else if ( (true) ) {
                    alt28=2;
                }
                else {
                    if (state.backtracking>0) {state.failed=true; return retval;}
                    NoViableAltException nvae =
                        new NoViableAltException("", 28, 1, input);

                    throw nvae;
                }
            }
            else {
                if (state.backtracking>0) {state.failed=true; return retval;}
                NoViableAltException nvae =
                    new NoViableAltException("", 28, 0, input);

                throw nvae;
            }
            switch (alt28) {
                case 1 :
                    // org/brackit/server/node/index/definition/IndexDef.g:351:5: ( NCNAME COLON )=>prefix= NCNAME COLON local= NCNAME
                    {
                    prefix=(Token)match(input,NCNAME,FOLLOW_NCNAME_in_qname968); if (state.failed) return retval;
                    match(input,COLON,FOLLOW_COLON_in_qname970); if (state.failed) return retval;
                    local=(Token)match(input,NCNAME,FOLLOW_NCNAME_in_qname974); if (state.failed) return retval;
                    if ( state.backtracking==0 ) {
                       retval.result = (prefix!=null?prefix.getText():null) + ":" + (local!=null?local.getText():null); 
                    }

                    }
                    break;
                case 2 :
                    // org/brackit/server/node/index/definition/IndexDef.g:352:4: fullName= NCNAME
                    {
                    fullName=(Token)match(input,NCNAME,FOLLOW_NCNAME_in_qname986); if (state.failed) return retval;
                    if ( state.backtracking==0 ) {
                       retval.result = (fullName!=null?fullName.getText():null); 
                    }

                    }
                    break;

            }
            retval.stop = input.LT(-1);

        }

        	catch (RecognitionException re)
        	{
        		// TODO: set the reported error message as the exception message
        		reportError(re);
        		throw re;
        	}
        finally {
        }
        return retval;
    }
    // $ANTLR end "qname"

    // $ANTLR start synpred1_IndexDef
    public final void synpred1_IndexDef_fragment() throws RecognitionException {   
        // org/brackit/server/node/index/definition/IndexDef.g:221:4: ( LPAREN INCLUDING )
        // org/brackit/server/node/index/definition/IndexDef.g:221:5: LPAREN INCLUDING
        {
        match(input,LPAREN,FOLLOW_LPAREN_in_synpred1_IndexDef333); if (state.failed) return ;
        match(input,INCLUDING,FOLLOW_INCLUDING_in_synpred1_IndexDef335); if (state.failed) return ;

        }
    }
    // $ANTLR end synpred1_IndexDef

    // $ANTLR start synpred2_IndexDef
    public final void synpred2_IndexDef_fragment() throws RecognitionException {   
        // org/brackit/server/node/index/definition/IndexDef.g:351:5: ( NCNAME COLON )
        // org/brackit/server/node/index/definition/IndexDef.g:351:6: NCNAME COLON
        {
        match(input,NCNAME,FOLLOW_NCNAME_in_synpred2_IndexDef959); if (state.failed) return ;
        match(input,COLON,FOLLOW_COLON_in_synpred2_IndexDef961); if (state.failed) return ;

        }
    }
    // $ANTLR end synpred2_IndexDef

    // Delegated rules

    public final boolean synpred2_IndexDef() {
        state.backtracking++;
        int start = input.mark();
        try {
            synpred2_IndexDef_fragment(); // can never throw exception
        } catch (RecognitionException re) {
            System.err.println("impossible: "+re);
        }
        boolean success = !state.failed;
        input.rewind(start);
        state.backtracking--;
        state.failed=false;
        return success;
    }
    public final boolean synpred1_IndexDef() {
        state.backtracking++;
        int start = input.mark();
        try {
            synpred1_IndexDef_fragment(); // can never throw exception
        } catch (RecognitionException re) {
            System.err.println("impossible: "+re);
        }
        boolean success = !state.failed;
        input.rewind(start);
        state.backtracking--;
        state.failed=false;
        return success;
    }


 

    public static final BitSet FOLLOW_indexDefinition_in_index70 = new BitSet(new long[]{0x0000000000000000L});
    public static final BitSet FOLLOW_EOF_in_index75 = new BitSet(new long[]{0x0000000000000002L});
    public static final BitSet FOLLOW_CREATE_in_indexDefinition94 = new BitSet(new long[]{0x0000000000000F40L});
    public static final BitSet FOLLOW_contentIndex_in_indexDefinition105 = new BitSet(new long[]{0x0000004000000022L});
    public static final BitSet FOLLOW_casIndex_in_indexDefinition115 = new BitSet(new long[]{0x0000004000000022L});
    public static final BitSet FOLLOW_pathIndex_in_indexDefinition125 = new BitSet(new long[]{0x0000004000000022L});
    public static final BitSet FOLLOW_elementIndex_in_indexDefinition135 = new BitSet(new long[]{0x0000004000000022L});
    public static final BitSet FOLLOW_inContainer_in_indexDefinition146 = new BitSet(new long[]{0x0000000000000022L});
    public static final BitSet FOLLOW_TERMINATOR_in_indexDefinition155 = new BitSet(new long[]{0x0000000000000002L});
    public static final BitSet FOLLOW_CONTENT_in_contentIndex181 = new BitSet(new long[]{0x0000000000000080L});
    public static final BitSet FOLLOW_INDEX_in_contentIndex183 = new BitSet(new long[]{0x0000000002100002L});
    public static final BitSet FOLLOW_typeDef_in_contentIndex187 = new BitSet(new long[]{0x0000000000100002L});
    public static final BitSet FOLLOW_forContent_in_contentIndex192 = new BitSet(new long[]{0x0000000000000002L});
    public static final BitSet FOLLOW_UNIQUE_in_casIndex227 = new BitSet(new long[]{0x0000000000000200L});
    public static final BitSet FOLLOW_CAS_in_casIndex238 = new BitSet(new long[]{0x0000000000000080L});
    public static final BitSet FOLLOW_INDEX_in_casIndex240 = new BitSet(new long[]{0x00000000C0000000L});
    public static final BitSet FOLLOW_pathDef_in_casIndex244 = new BitSet(new long[]{0x0000000002800002L});
    public static final BitSet FOLLOW_typeDef_in_casIndex248 = new BitSet(new long[]{0x0000000000800002L});
    public static final BitSet FOLLOW_clustering_in_casIndex253 = new BitSet(new long[]{0x0000000000000002L});
    public static final BitSet FOLLOW_PATH_in_pathIndex277 = new BitSet(new long[]{0x0000000000000080L});
    public static final BitSet FOLLOW_INDEX_in_pathIndex279 = new BitSet(new long[]{0x00000000C0000000L});
    public static final BitSet FOLLOW_pathDef_in_pathIndex283 = new BitSet(new long[]{0x0000000000800002L});
    public static final BitSet FOLLOW_clustering_in_pathIndex287 = new BitSet(new long[]{0x0000000000000002L});
    public static final BitSet FOLLOW_ELEMENT_in_elementIndex321 = new BitSet(new long[]{0x0000000000000080L});
    public static final BitSet FOLLOW_INDEX_in_elementIndex323 = new BitSet(new long[]{0x0000000000801002L});
    public static final BitSet FOLLOW_includeElementDef_in_elementIndex342 = new BitSet(new long[]{0x0000000000800002L});
    public static final BitSet FOLLOW_excludeElementDef_in_elementIndex353 = new BitSet(new long[]{0x0000000000800002L});
    public static final BitSet FOLLOW_clustering_in_elementIndex366 = new BitSet(new long[]{0x0000000000000002L});
    public static final BitSet FOLLOW_LPAREN_in_includeElementDef393 = new BitSet(new long[]{0x0000000000002000L});
    public static final BitSet FOLLOW_INCLUDING_in_includeElementDef395 = new BitSet(new long[]{0x0000000400000000L});
    public static final BitSet FOLLOW_qnameWithCluster_in_includeElementDef399 = new BitSet(new long[]{0x000000000000C000L});
    public static final BitSet FOLLOW_COMMA_in_includeElementDef405 = new BitSet(new long[]{0x0000000400000000L});
    public static final BitSet FOLLOW_qnameWithCluster_in_includeElementDef407 = new BitSet(new long[]{0x000000000000C000L});
    public static final BitSet FOLLOW_RPAREN_in_includeElementDef412 = new BitSet(new long[]{0x0000000000000002L});
    public static final BitSet FOLLOW_LPAREN_in_excludeElementDef434 = new BitSet(new long[]{0x0000000000010000L});
    public static final BitSet FOLLOW_EXCLUDING_in_excludeElementDef436 = new BitSet(new long[]{0x0000000400000000L});
    public static final BitSet FOLLOW_qname_in_excludeElementDef442 = new BitSet(new long[]{0x000000000000C000L});
    public static final BitSet FOLLOW_COMMA_in_excludeElementDef453 = new BitSet(new long[]{0x0000000400000000L});
    public static final BitSet FOLLOW_qname_in_excludeElementDef460 = new BitSet(new long[]{0x000000000000C000L});
    public static final BitSet FOLLOW_RPAREN_in_excludeElementDef471 = new BitSet(new long[]{0x0000000000000002L});
    public static final BitSet FOLLOW_qname_in_qnameWithCluster493 = new BitSet(new long[]{0x0000000000020002L});
    public static final BitSet FOLLOW_AT_in_qnameWithCluster498 = new BitSet(new long[]{0x00000000000C0000L});
    public static final BitSet FOLLOW_SPLID_in_qnameWithCluster505 = new BitSet(new long[]{0x0000000000000002L});
    public static final BitSet FOLLOW_PCR_in_qnameWithCluster514 = new BitSet(new long[]{0x0000000000000002L});
    public static final BitSet FOLLOW_FOR_in_forContent542 = new BitSet(new long[]{0x0000000000600800L});
    public static final BitSet FOLLOW_ELEMENT_in_forContent551 = new BitSet(new long[]{0x0000000000000040L});
    public static final BitSet FOLLOW_ATTRIBUTE_in_forContent560 = new BitSet(new long[]{0x0000000000000040L});
    public static final BitSet FOLLOW_ALL_in_forContent570 = new BitSet(new long[]{0x0000000000000040L});
    public static final BitSet FOLLOW_CONTENT_in_forContent581 = new BitSet(new long[]{0x0000000000000002L});
    public static final BitSet FOLLOW_WITH_in_clustering597 = new BitSet(new long[]{0x00000000000C0000L});
    public static final BitSet FOLLOW_SPLID_in_clustering606 = new BitSet(new long[]{0x0000000001000000L});
    public static final BitSet FOLLOW_PCR_in_clustering615 = new BitSet(new long[]{0x0000000001000000L});
    public static final BitSet FOLLOW_CLUSTERING_in_clustering625 = new BitSet(new long[]{0x0000000000000002L});
    public static final BitSet FOLLOW_OF_TYPE_in_typeDef641 = new BitSet(new long[]{0x000000003C000000L});
    public static final BitSet FOLLOW_type_in_typeDef645 = new BitSet(new long[]{0x0000000000000002L});
    public static final BitSet FOLLOW_STRING_in_type663 = new BitSet(new long[]{0x0000000000000002L});
    public static final BitSet FOLLOW_DOUBLE_in_type670 = new BitSet(new long[]{0x0000000000000002L});
    public static final BitSet FOLLOW_LONG_in_type677 = new BitSet(new long[]{0x0000000000000002L});
    public static final BitSet FOLLOW_INTEGER_in_type684 = new BitSet(new long[]{0x0000000000000002L});
    public static final BitSet FOLLOW_set_in_pathDef708 = new BitSet(new long[]{0x0000003900004000L});
    public static final BitSet FOLLOW_path_in_pathDef720 = new BitSet(new long[]{0x0000000000004002L});
    public static final BitSet FOLLOW_COMMA_in_pathDef731 = new BitSet(new long[]{0x0000003900004000L});
    public static final BitSet FOLLOW_path_in_pathDef738 = new BitSet(new long[]{0x0000000000004002L});
    public static final BitSet FOLLOW_namedstep_in_path764 = new BitSet(new long[]{0x0000003900000002L});
    public static final BitSet FOLLOW_attributestep_in_path768 = new BitSet(new long[]{0x0000000000000002L});
    public static final BitSet FOLLOW_CHILD_ATT_in_attributestep794 = new BitSet(new long[]{0x0000000600000000L});
    public static final BitSet FOLLOW_WILDCARD_in_attributestep798 = new BitSet(new long[]{0x0000000000000002L});
    public static final BitSet FOLLOW_NCNAME_in_attributestep806 = new BitSet(new long[]{0x0000000000000002L});
    public static final BitSet FOLLOW_DESC_ATT_in_attributestep819 = new BitSet(new long[]{0x0000000600000000L});
    public static final BitSet FOLLOW_WILDCARD_in_attributestep823 = new BitSet(new long[]{0x0000000000000002L});
    public static final BitSet FOLLOW_NCNAME_in_attributestep831 = new BitSet(new long[]{0x0000000000000002L});
    public static final BitSet FOLLOW_CHILD_in_namedstep860 = new BitSet(new long[]{0x0000000600000000L});
    public static final BitSet FOLLOW_WILDCARD_in_namedstep864 = new BitSet(new long[]{0x0000000000000002L});
    public static final BitSet FOLLOW_NCNAME_in_namedstep872 = new BitSet(new long[]{0x0000000000000002L});
    public static final BitSet FOLLOW_DESC_in_namedstep885 = new BitSet(new long[]{0x0000000600000000L});
    public static final BitSet FOLLOW_WILDCARD_in_namedstep889 = new BitSet(new long[]{0x0000000000000002L});
    public static final BitSet FOLLOW_NCNAME_in_namedstep897 = new BitSet(new long[]{0x0000000000000002L});
    public static final BitSet FOLLOW_IN_in_inContainer921 = new BitSet(new long[]{0x0000008000000000L});
    public static final BitSet FOLLOW_cntNo_in_inContainer925 = new BitSet(new long[]{0x0000000000000002L});
    public static final BitSet FOLLOW_DIGITS_in_cntNo939 = new BitSet(new long[]{0x0000000000000002L});
    public static final BitSet FOLLOW_NCNAME_in_qname968 = new BitSet(new long[]{0x0000010000000000L});
    public static final BitSet FOLLOW_COLON_in_qname970 = new BitSet(new long[]{0x0000000400000000L});
    public static final BitSet FOLLOW_NCNAME_in_qname974 = new BitSet(new long[]{0x0000000000000002L});
    public static final BitSet FOLLOW_NCNAME_in_qname986 = new BitSet(new long[]{0x0000000000000002L});
    public static final BitSet FOLLOW_LPAREN_in_synpred1_IndexDef333 = new BitSet(new long[]{0x0000000000002000L});
    public static final BitSet FOLLOW_INCLUDING_in_synpred1_IndexDef335 = new BitSet(new long[]{0x0000000000000002L});
    public static final BitSet FOLLOW_NCNAME_in_synpred2_IndexDef959 = new BitSet(new long[]{0x0000010000000000L});
    public static final BitSet FOLLOW_COLON_in_synpred2_IndexDef961 = new BitSet(new long[]{0x0000000000000002L});

}