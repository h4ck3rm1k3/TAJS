/*
 * Copyright 2012 Aarhus University
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dk.brics.tajs.lattice;

import static dk.brics.tajs.util.Collections.newMap;
import static dk.brics.tajs.util.Collections.newSet;
import static dk.brics.tajs.util.Collections.sortedEntries;

import dk.brics.tajs.util.AnalysisException;
import dk.brics.tajs.util.Collections;
import java.util.Map;
import java.util.Set;

import dk.brics.tajs.options.Options;
import dk.brics.tajs.util.Strings;

/**
 * Abstract object.
 * Mutable.
 */
public final class Obj {
	
	private Map<String,Value> properties;
	private boolean writable_properties; // for copy-on-write
	
	private Value default_array_property; // represents all other possible properties that are valid array indices
	private Value default_nonarray_property; // represents all other possible properties
	
	private Value internal_prototype; // the [[Prototype]] property
	private Value internal_value; // the [[Value]] property
	
	private ScopeChain scope; // the [[Scope]] property, null if empty or unknown
	private boolean scope_unknown; // if set, the value of scope is unknown (and the 'scope' field is then not used)
	
	private static int number_of_objs_created;
	private static int number_of_makewritable_properties;
	
	private Obj() {
		number_of_objs_created++;
	}

	/**
	 * Creates a new abstract object as a copy of the given.
	 */
	public Obj(Obj x) {
		setTo(x);
		number_of_objs_created++;
	}
	
	/**
	 * Sets this object to be a copy of the given.
	 */
	public void setTo(Obj x) {
		default_nonarray_property = x.default_nonarray_property;
		default_array_property = x.default_array_property;
		internal_prototype = x.internal_prototype;
		internal_value = x.internal_value;
		scope = x.scope;
		scope_unknown = x.scope_unknown;
		if (Options.isCopyOnWriteDisabled()) {
			properties = newMap(x.properties);
		} else {
			properties = x.properties;
			scope = x.scope;
			x.writable_properties = writable_properties = false;
		}
	}
	
	/**
	 * Constructs an abstract object where all properties are absent (but modified) and scope is set to empty.
	 */
	public static Obj makeAbsentModified() {
		Obj obj = new Obj();
		obj.properties = Collections.newMap();
		obj.default_nonarray_property = obj.default_array_property = obj.internal_prototype = obj.internal_value = Value.makeAbsentModified();
		return obj;
	}
	
	/**
	 * Constructs an abstract object where all properties are none and scope is set to empty.
	 */
	public static Obj makeNone() {
		Obj obj = new Obj();
		obj.properties = Collections.newMap();
		obj.default_nonarray_property = obj.default_array_property = obj.internal_prototype = obj.internal_value = Value.makeNone();
		return obj;
	}
	
	/**
	 * Constructs an abstract object where all properties have 'unknown' value.
	 */
	public static Obj makeUnknown() {
		Obj obj = new Obj();
		obj.properties = Collections.newMap();
		obj.writable_properties = false;
		obj.default_array_property = obj.default_nonarray_property = obj.internal_prototype = obj.internal_value = Value.makeUnknown();
		obj.scope_unknown = true;
		return obj;
	}
	
	/**
	 * Checks whether all properties have 'unknown' value.
	 */
	public boolean isUnknown() {
		for (Value v : properties.values())
			if (!v.isUnknown())
				return false;
		return default_array_property.isUnknown() && default_nonarray_property.isUnknown() && internal_prototype.isUnknown()
		&& internal_value.isUnknown() && scope_unknown;
	}
	
	/**
	 * Checks whether all properties have the none value.
	 */
	public boolean isNone() {
		for (Value v : properties.values())
			if (!v.isNone())
				return false;
		return default_array_property.isNone() && default_nonarray_property.isNone() && internal_prototype.isNone()
		&& internal_value.isNone() && !scope_unknown && scope == null;
	}
	
	/**
	 * Summarizes the object labels in this object.
	 */
	public void summarize(Summarized s) {
		Map<String,Value> newproperties = newMap();
		for (Map.Entry<String,Value> me : properties.entrySet())
			newproperties.put(me.getKey(), me.getValue().summarize(s));
		writable_properties = true;
		properties = newproperties;
		default_array_property = default_array_property.summarize(s);
		default_nonarray_property = default_nonarray_property.summarize(s);
		internal_prototype = internal_prototype.summarize(s);
		internal_value = internal_value.summarize(s);
		scope = ScopeChain.summarize(scope, s);
	}
	
	/**
	 * Replaces all definitely non-modified properties in this object by the corresponding properties of other. 
	 */
	public void replaceNonModifiedParts(Obj other) {
		Map<String,Value> newproperties = newMap();
		for (Map.Entry<String,Value> me : properties.entrySet()) {
			Value v = me.getValue();
			if (!v.isMaybeModified()) // property is definitely not modified, so replace it (don't consider the defaults here)
				v = other.properties.get(me.getKey());
			if (v != null) // if the property is definitely not modified *and* it doesn't appear in the other object, then remove it
				newproperties.put(me.getKey(), v);
		}
		boolean default_array_property_maybe_modified = default_array_property.isMaybeModified();
		boolean default_nonarray_property_maybe_modified = default_nonarray_property.isMaybeModified();
		if (!default_array_property_maybe_modified || !default_nonarray_property_maybe_modified)
			for (Map.Entry<String,Value> me : other.properties.entrySet())
				if (!newproperties.containsKey(me.getKey()) 
						&& (Strings.isArrayIndex(me.getKey()) ? !default_array_property_maybe_modified : !default_nonarray_property_maybe_modified))
					newproperties.put(me.getKey(), me.getValue());
		properties = newproperties;
		writable_properties = true;
		if (!default_array_property_maybe_modified)
			default_array_property = other.default_array_property;
		if (!default_nonarray_property_maybe_modified)
			default_nonarray_property = other.default_nonarray_property;
		if (!internal_prototype.isMaybeModified())
			internal_prototype = other.internal_prototype;
		if (!internal_value.isMaybeModified())
			internal_value = other.internal_value;
		if (scope_unknown && !other.scope_unknown) {
			scope = other.scope;
			scope_unknown = other.scope_unknown;
		}
	}
	
	/**
	 * Makes properties writable.
	 */
	private void makeWritableProperties() {
		if (writable_properties) 
			return;
		properties = newMap(properties);
		writable_properties = true;
		number_of_makewritable_properties++;
	}
	
	/**
	 * Returns the total number of Obj objects created.
	 */
	public static int getNumberOfObjsCreated() {
		return number_of_objs_created;
	}
	
	/**
	 * Resets the global counters.
	 */
	public static void reset() {
		number_of_objs_created = 0;
		number_of_makewritable_properties = 0;
	}

	/**
	 * Returns the total number of makeWritableProperties operations.
	 */
	public static int getNumberOfMakeWritablePropertiesCalls() {
		return number_of_makewritable_properties;
	}
	
	/**
	 * Returns the size of the properties map.
	 */
	public int getNumberOfProperties() {
		return properties.size();
	}
	
	/**
	 * Clears modified flags for all values.
	 */
	public void clearModified() {
		Map<String,Value> new_properties = newMap();
		for (Map.Entry<String,Value> me : properties.entrySet())
			new_properties.put(me.getKey(), me.getValue().restrictToNotModified());
		properties = new_properties;
		writable_properties = true;
		default_nonarray_property = default_nonarray_property.restrictToNotModified();
		default_array_property = default_array_property.restrictToNotModified();
		internal_prototype = internal_prototype.restrictToNotModified();
		internal_value = internal_value.restrictToNotModified();
	}
	
	/**
	 * Returns the value of the given property, considering defaults if necessary.
	 * Never returns null, may return 'unknown'.
	 */
	public Value getProperty(String propertyname) {
		Value v = properties.get(propertyname);
		if (v == null)
			if (Strings.isArrayIndex(propertyname)) 
				v = getDefaultArrayProperty();
			else 
				v = getDefaultNonArrayProperty();
		return v;
	}
	
	/**
	 * Sets the given property.
	 */
	public void setProperty(String propertyname, Value v) {
		makeWritableProperties();
		properties.put(propertyname, v);
	}

	/**
	 * Returns all property names, excluding the defaults and internal properties.
	 */
	public Set<String> getPropertyNames() {
		return properties.keySet();
	}
	
	/**
	 * Returns all properties, excluding the defaults and internal properties.
	 * The map is *not* made writable.
	 */
	public Map<String,Value> getProperties() {
		return properties;
	}
	
	/**
	 * Sets the property map.
	 * Also marks the property map as writable.
	 */
	public void setProperties(Map<String,Value> properties) {
		this.properties = properties;
		writable_properties = true;
	}
	
	/**
	 * Returns the value of the default array property.
	 */
	public Value getDefaultArrayProperty() {
		return default_array_property;
	}
	
	/**
	 * Sets the value of the default array property.
	 */
	public void setDefaultArrayProperty(Value v) {
		if (!v.isUnknown() && v.isMaybePresent() && !v.isMaybeAbsent())
			throw new AnalysisException("Illegal default array property: " + v);
		default_array_property = v;
	}
	
	/**
	 * Returns the value of the default non-array property.
	 */
	public Value getDefaultNonArrayProperty() {
		return default_nonarray_property;
	}
	
	/**
	 * Sets the value of the default non-array property.
	 */
	public void setDefaultNonArrayProperty(Value v) {
		if (!v.isUnknown() && v.isMaybePresent() && !v.isMaybeAbsent())
			throw new AnalysisException("Illegal default nonarray property: " + v);
		default_nonarray_property = v;
	}

	/**
	 * Checks whether some non-array property is unknown, including the default.
	 */
	public boolean isSomeNonArrayPropertyUnknown() {
		if (default_nonarray_property.isUnknown())
			return true;
		for (Map.Entry<String,Value> me : properties.entrySet())
			if (me.getValue().isUnknown() && !Strings.isArrayIndex(me.getKey()))
				return true;
		return false;
	}
	
	/**
	 * Returns the value of the internal [[Value]] property.
	 */
	public Value getInternalValue() {
		return internal_value;
	}
	
	/**
	 * Sets the internal [[Value]] property.
	 */
	public void setInternalValue(Value v) {
		internal_value = v;
	}
	
	/**
	 * Returns the value of the internal [[Prototype]] property.
	 */
	public Value getInternalPrototype() {
		return internal_prototype;
	}
	
	/**
	 * Sets the internal [[Prototype]] property.
	 */
	public void setInternalPrototype(Value v) {
		internal_prototype = v;
	}
	
	/**
	 * Returns the value of the internal [[Scope]] property.
	 * Assumed to be non-'unknown'.
	 */
	public ScopeChain getScopeChain() {
		if (scope_unknown)
			throw new AnalysisException("Calling getScopeChain when scope is 'unknown'");
		return scope;
	}
	
	/**
	 * Sets the internal [[Scope]] property.
	 */
	public void setScopeChain(ScopeChain scope) {
		this.scope = scope;
		scope_unknown = false;
	}
	
	/**
	 * Adds to the internal [[Scope]] property.
	 * @return true if changed
	 */
	public boolean addToScopeChain(ScopeChain newscope) {
		if (scope_unknown)
			throw new AnalysisException("Calling addToScopeChain when scope is 'unknown'");
		ScopeChain res = ScopeChain.add(scope, newscope);
		boolean changed = res != null && !res.equals(scope);
		scope = res;
		return changed;
	}
	
	/**
	 * Returns true if internal [[Scope]] property is 'unknown'.
	 */
	public boolean isScopeChainUnknown() {
		return scope_unknown;
	}	
	
	/**
	 * Replaces all occurrences of oldlabel by newlabel.
	 * Does not change modified flags.
	 * Ignores 'unknown' values.
	 */
	public void replaceObjectLabel(ObjectLabel oldlabel, ObjectLabel newlabel, Map<ScopeChain,ScopeChain> cache) {
		Map<String,Value> newproperties = newMap();
		for (Map.Entry<String,Value> me : properties.entrySet())
			newproperties.put(me.getKey(), me.getValue().replaceObjectLabel(oldlabel, newlabel));
		properties = newproperties;
		scope = ScopeChain.replaceObjectLabel(scope, oldlabel, newlabel, cache);
		default_nonarray_property = default_nonarray_property.replaceObjectLabel(oldlabel, newlabel);
		default_array_property = default_array_property.replaceObjectLabel(oldlabel, newlabel);
		internal_prototype = internal_prototype.replaceObjectLabel(oldlabel, newlabel);
		internal_value = internal_value.replaceObjectLabel(oldlabel, newlabel);
		writable_properties = true;
	}
	
	/**
	 * Replaces all object labels according to the given map.
	 * Does not change modified flags. Object labels not in the key set of the map are unchanged.
	 * Ignores 'unknown' values.
	 */
	public void replaceObjectLabels(Map<ObjectLabel, ObjectLabel> m, Map<ScopeChain,ScopeChain> cache) {
		Map<String,Value> newproperties = newMap();
		for (Map.Entry<String,Value> me : properties.entrySet())
			newproperties.put(me.getKey(), me.getValue().replaceObjectLabels(m));
		properties = newproperties;
		scope = ScopeChain.replaceObjectLabels(scope, m, cache);
		default_nonarray_property = default_nonarray_property.replaceObjectLabels(m);
		default_array_property = default_array_property.replaceObjectLabels(m);
		internal_prototype = internal_prototype.replaceObjectLabels(m);
		internal_value = internal_value.replaceObjectLabels(m);
		writable_properties = true;
	}
	
	/**
	 * Checks whether the given abstract object is equal to this one.
	 */
	@Override
	public boolean equals(Object obj) {
		if (obj == this)
			return true;
		if (!(obj instanceof Obj))
			return false;
		Obj x = (Obj)obj;
		if ((scope == null) != (x.scope == null))
			return false;
		return properties.equals(x.properties) &&
		(scope == null || x.scope == null || scope.equals(x.scope)) && 
		(scope_unknown == x.scope_unknown) && 
		default_nonarray_property.equals(x.default_nonarray_property) && 
		default_array_property.equals(x.default_array_property) && 
		internal_prototype.equals(x.internal_prototype) &&
		internal_value.equals(x.internal_value);
	}
	
	/**
	 * Returns a description of the changes from the old object to this object.
	 * It is assumed that the old object is less than this object 
	 * and that no explicit property has been moved to default_other_property or default_array_property.
	 */
	public void diff(Obj old, StringBuilder b) {
		for (Map.Entry<String,Value> me : sortedEntries(properties)) {
			Value v = old.properties.get(me.getKey());
			if (v == null) {
				b.append("\n        new property: ").append(me.getKey());
			} else if (!me.getValue().equals(v)) {
				b.append("\n        changed property: ").append(me.getKey()).append(": ");
				me.getValue().diff(v, b);
				b.append(" was: ").append(v);
			}
		}
		if (!default_array_property.equals(old.default_array_property)) {
			b.append("\n        changed default_array_property: ");
			default_array_property.diff(old.default_array_property, b);
			b.append(" was: ").append(old.default_array_property);
		}
		if (!default_nonarray_property.equals(old.default_nonarray_property)) {
			b.append("\n        changed default_nonarray_property: ");
			default_nonarray_property.diff(old.default_nonarray_property, b);
			b.append(" was: ").append(old.default_nonarray_property);
		}
		if (!internal_prototype.equals(old.internal_prototype)) {
			b.append("\n        changed internal_prototype: ");
			internal_prototype.diff(old.internal_prototype, b);
			b.append(" was: ").append(old.internal_prototype);
		}
		if (!internal_value.equals(old.internal_value)) {
			b.append("\n        changed internal_value: ");
			internal_value.diff(old.internal_value, b);
			b.append(" was: ").append(old.internal_value);
		}	
		if (scope_unknown != old.scope_unknown) {
			b.append("\n        changed scope_unknown");
		}
	}
	
	/**
	 * Computes the hash code for this abstract object.
	 */
	@Override
	public int hashCode() {
		return properties.hashCode() * 3
		+ (scope != null ? scope.hashCode() * 7 : 0) 
		+ (scope_unknown ? 13 : 0)
		+ internal_prototype.hashCode() * 11 
		+ internal_value.hashCode() * 113 
		+ default_nonarray_property.hashCode() * 23 
		+ default_array_property.hashCode() * 31;
	}

	/**
	 * Produces a string description of this abstract object.
	 */
	@Override
	public String toString() {
		StringBuilder b = new StringBuilder();
		boolean any = false;
		b.append("{");
		if (default_array_property.isNone()) {
			any = true;
			b.append("<none>");
		}
		for (Map.Entry<String,Value> me : sortedEntries(properties)) {
			Value v = me.getValue();
			if (any)
				b.append(",");
			else
				any = true;
			b.append(Strings.escape(me.getKey())).append(":").append(v);
		}
		if (default_array_property.isMaybePresentOrUnknown()) {
			if (any)
				b.append(",");
			else
				any = true;
			b.append("[[DefaultArray]]=").append(default_array_property);
		}
		if (default_nonarray_property.isMaybePresentOrUnknown()) {
			if (any)
				b.append(",");
			else
				any = true;
			b.append("[[DefaultNonArray]]=").append(default_nonarray_property);
		}
		if (internal_prototype.isMaybePresentOrUnknown()) {
			if (any)
				b.append(",");
			else
				any = true;
			b.append("[[Prototype]]=").append(internal_prototype);
		}
		if (internal_value.isMaybePresentOrUnknown()) {
			if (any)
				b.append(",");
			else
				any = true;
			b.append("[[Value]]=").append(internal_value);
		}
		if (scope != null || scope_unknown) {
			if (any)
				b.append(",");
			else
				any = true;
			b.append("[[Scope]]=");
			if (scope != null)
				b.append(scope);
			else
				b.append("?");
		}
		b.append("}");
		return b.toString();
	}
	
	/**
	 * Prints the maybe modified properties.
	 * Internal properties are ignored.
	 */
	public String printModified() {
		StringBuilder b = new StringBuilder();
		for (Map.Entry<String,Value> me : sortedEntries(properties)) {
			Value v = me.getValue();
			if (v.isMaybeModified() && v.isMaybePresentOrUnknown())
				b.append("\n    ").append(Strings.escape(me.getKey())).append(": ").append(v);
		}
		if ((default_array_property.isMaybeModified()) && default_array_property.isMaybePresentOrUnknown()) 
			b.append("\n    ").append("[[DefaultArray]] = ").append(default_array_property);
		if ((default_nonarray_property.isMaybeModified()) && default_nonarray_property.isMaybePresentOrUnknown()) 
			b.append("\n    ").append("[[DefaultNonArray]] = ").append(default_nonarray_property);
		if (internal_prototype.isMaybeModified() && internal_prototype.isMaybePresentOrUnknown())
			b.append("\n    [[Prototype]] = ").append(internal_prototype);
		if (internal_value.isMaybeModified() && internal_value.isMaybePresentOrUnknown())
			b.append("\n    [[Value]] = ").append(internal_value);
		return b.toString();
	}	
	
	/**
	 * Returns the set of all object labels used in this abstract object
	 * 'unknown' values are ignored.
	 */
	public Set<ObjectLabel> getAllObjectLabels() {
		Set<ObjectLabel> objlabels = newSet();
		for (Value v : properties.values())
			objlabels.addAll(v.getObjectLabels());
		objlabels.addAll(default_array_property.getObjectLabels());
		objlabels.addAll(default_nonarray_property.getObjectLabels());
		objlabels.addAll(internal_prototype.getObjectLabels());
		objlabels.addAll(internal_value.getObjectLabels());
		for (Set<ObjectLabel> ls : ScopeChain.iterable(scope))
			objlabels.addAll(ls);
		return objlabels;
	}
	
	/**
	 * Returns the designated value.
	 */
	public Value getValue(PropertyReference prop) {
		switch (prop.getKind()) {
		case ORDINARY:
			return getProperty(prop.getPropertyName());
		case DEFAULT_ARRAY:
			return getDefaultArrayProperty();
		case DEFAULT_NONARRAY:
			return getDefaultNonArrayProperty();
		case INTERNAL_VALUE:
			return getInternalValue();
		case INTERNAL_PROTOTYPE:
			return getInternalPrototype();
		default:
			throw new AnalysisException("Unexpected property reference kind");
		}		
	}

	/**
	 * Trims this object according to the given existing object.
	 */
	public void trim(Obj obj) {
		// reduce those properties that are unknown or polymorphic in obj
		Map<String,Value> new_properties = newMap();
		for (Map.Entry<String,Value> me : properties.entrySet()) {
			String propertyname = me.getKey();
			Value v = me.getValue();
			Value obj_v = obj.getProperty(propertyname);
			new_properties.put(propertyname, v.trim(obj_v));
		}
		properties = new_properties;
		writable_properties = true;
		default_array_property = default_array_property.trim(obj.default_array_property); 
		default_nonarray_property = default_nonarray_property.trim(obj.default_nonarray_property); 
		internal_value = internal_value.trim(obj.internal_value); 
		internal_prototype = internal_prototype.trim(obj.internal_prototype); 
		if (obj.scope_unknown) { // TODO: scope chain polymorphic?
			scope = null;
			scope_unknown = true;
		}
	}

	/**
	 * Removes the parts of this object that are also in the given object.
	 * It is assumed that this object subsumes the given object, but the defaults may not cover the same properties.
	 */
	public void remove(Obj obj) {
		Map<String,Value> new_properties = newMap();
		for (Map.Entry<String,Value> me : properties.entrySet()) {
			String propertyname = me.getKey();
			Value value = me.getValue();
			Value other_value = obj.getProperty(propertyname); // may look up in default
			Value new_value = value.remove(other_value);
			new_properties.put(propertyname, new_value);
		}
		properties = new_properties;
		writable_properties = true;
		// careful with defaults when they don't cover the same properties - here, obj.default
		// has already been propagated further (to the function entry state), so we don't need
		// to consider which non-default properties in obj correspond to this.default
		default_array_property = default_array_property.remove(obj.default_array_property);
		default_nonarray_property = default_nonarray_property.remove(obj.default_nonarray_property);
		internal_prototype = internal_prototype.remove(obj.internal_prototype);
		internal_value = internal_value.remove(obj.internal_value);
		scope = ScopeChain.remove(scope,  obj.scope);
	}
}
