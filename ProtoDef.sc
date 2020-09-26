// Definition of a prototype
// an Environment that will be referenced by prototypes

ProtoDef : Environment{

	classvar <defs;


	var <>defName;
	var <>parentName;

	*loadProtodefs{|dir,dirName=nil|
		var loadedDefNames, errors = [];
		dirName = dirName ? "protodefs";
		dir = (dir ?? {thisProcess.nowExecutingPath.dirname}) +/+ dirName;
		dir = dir +/+ "*.scd";
		loadedDefNames = dir.loadPaths(action:{|path,def|
			"*** [ProtoDefs] Loading: %".format(path).postln;
			def ?? {
				errors = errors.add(path)
			}
		}).select(_.notNil).collect(_.defName);

		"*** [ProtoDefs] % Loaded:".format(loadedDefNames.size).postln;
		loadedDefNames.postln;

		if(errors.size>0){
			error(
				"[ProtoDefs] % ".format(errors.size) ++
				"definition% failed".format((errors.size!=1).if('s',''))
			);
			errors.do({|err| err.basename.error})
		};
	}

	*initClass {
		defs = IdentityDictionary.new;
	}

	*fromObject{|name,copyFrom,defBlock=nil|
		var obj = super.newFrom(copyFrom ? ()).know_(true);
		defs[name] = obj;
		defs[name].defName = name;
		obj[\initDef] !? {
			obj.initDef();
		};

		defBlock !? {
			defs[name].use(defBlock);
		};

		^defs[name].defName;
	}

	*new {|name,defBlock=nil,parent=nil|
		defs[name] = defs[name] ? super.new(know:true);
		defs[name].defName = name;

		defBlock !? {
			defs[name].use(defBlock);
		};

		parent !? {
			defs[name].parentName = parent;
			defs[name].parent = ProtoDef(parent);
		}

		^defs[name];
	}

	/*getClassCode {
	var code = this.defName.asString ++"{\n";
	this.select{|val| val.isFunction}.keysValuesDo{|name,func|
	code = code ++ (name++func.def.sourceCode++";\n");
	};
	code = code ++ "}\n";
	^code;
	}*/


}


// A Prototype references a ProtoDef
// calling its methods and values when it hasn't overridden them with its own
/*
// Define a prototype, and implicitly a ProtoDef called \testProcess
q = Prototype(\testProcess)
ProtoDef(\testProcess).hello = { "Hello World".postln }
q.hello
ProtoDef(\testProcess).hello = {|q| "Hello World, my name is %\n".postf(q.name) }
q.def.name = "Default"
q.hello
q.name = "Specific"
q.hello
q.clear
q.hello
w = Prototype(\testProcess)
w.hello

// define prototype with use:
w.def.use{|q|
q.init = {|q|
// in a method, q is the instance, and values are stored in the instance
q.freq = rrand(20,20000);
q.synths = List[];
q.makeSynth = {|q| q.synths.add(Synth(\default,[\freq,q.freq]))};
q.freeAll = {|q,play=true| q.synths.do{|sy| sy.free}};
};
// outside of the method, q is the definition, and values are shared among instances
q.sharedModulation = 20;
}

// init methods
a init method on the def is called automatically on Prototype instantiation
a initDef method on the def is called automatically on ProtoDef creation
*/

Prototype : Environment{

	var <defName;

	*new{|name,beforeInit,args|
		^super.new().linkProtoDef(name,beforeInit,args)
	}

	linkProtoDef{|name,before,initArgs|
		defName = name;
		// beforeInit
		before !? {this.use(before)};
		// init
		this.def[\init] !? {this.init(*(initArgs?[]))};
	}

	overrideDef {
		ProtoDef.fromObject(defName,this);
	}

	def { ^ProtoDef(defName) }

	def_ {|name| defName = name }

	doesNotUnderstand { arg selector ... args;

		if (selector.isSetter) {
			selector = selector.asGetter;
			if(this.respondsTo(selector)) {
				warn(selector.asCompileString
					+ "exists a method name, so you can't use it as pseudo-method.")
			};
			^this[selector] = args[0];
		};

		^this.prTryProtoMethod(selector, args);
	}

	prTryProtoMethod {|methodName, args|
		var func = this[methodName] ? this.def[methodName];
		var result;

		func ?? { ^nil };
		/*if(this.class.useEnv){
		this.use{ result  = func.valueArray(args) };
		^result;
		}{*/
		{
			result = func.functionPerformList(\value, this, args);
		}.try{|err|
			error("[ProtoDef: %] '%' failed".format(this.defName,methodName));
			err.throw;
		}
		^result;
		//}
	}

	super {|methodName,args|
		var parent, func, result;
		parent = this.def.parent;
		parent ?? {
			"[ProtoDef: %] no parent def to call for 'super.%'"
			.format(this.defName, methodName).warn;
			^nil
		};
		func = this.def.parent[methodName];
		func ?? {
			"[ProtoDef: %] method '%' not found in parent def %"
			.format(this.defName, methodName, this.def.parentName).warn;
			^nil
		};

		try{
			result = func.functionPerformList(\value, this, args);
		} { |err|
			error(
				"[ProtoDef: % : %] '%' failed"
				.format(this.defName, this.def.parentName, methodName)
			);
			err.throw;
		}
		^result;
	}

	update { |...args| ^this.prTryProtoMethod(\update, args)}
	play { |...args| ^this.prTryProtoMethod(\play, args)}
	stop { |...args| ^this.prTryProtoMethod(\stop, args)}
	clear { |...args| ^this.prTryProtoMethod(\clear, args)}
	free { |...args| ^this.prTryProtoMethod(\free, args)}

}

Prot : Prototype{}