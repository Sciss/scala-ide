/*
 * Copyright 2005-2008 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.javaelements

import org.eclipse.core.resources.IFile
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants

import scala.tools.nsc.symtab.Flags

import scala.tools.eclipse.{ ScalaPresentationCompiler, ScalaSourceIndexer }

trait ScalaIndexBuilder { self : ScalaPresentationCompiler =>
  
  class IndexBuilderTraverser(indexer : ScalaSourceIndexer) extends Traverser {
    private var currentBuilder : Owner = new CompilationUnitBuilder
  
    def enclosingTypeNames(sym : Symbol): List[String] = {
      def enclosing(sym : Symbol) : List[String] =
        if (sym.owner.hasFlag(Flags.PACKAGE))
          Nil
        else {
          val owner = sym.owner 
          val name0 = owner.simpleName.toString
          val name = if (owner.isModuleClass) name0+"$" else name0
          name :: enclosing(owner)
        }
          
      enclosing(sym).reverse
    }
    
    trait Owner {
      def parent : Owner

      def compilationUnitBuilder : Owner = parent.compilationUnitBuilder
      
      def isPackage = false
      def isCtor = false
      def isTemplate = false
      def template : Owner = if (parent != null) parent.template else null

      def addPackage(p : PackageDef) : Owner = this
      def addClass(c : ClassDef) : Owner = this
      def addModule(m : ModuleDef) : Owner = this
      def addVal(v : ValDef) : Owner = this
      def addType(t : TypeDef) : Owner = this
      def addDef(d : DefDef) : Owner = this
      def addFunction(f : Function) : Owner = this
    }
    
    trait PackageOwner extends Owner { self =>
      override def addPackage(p : PackageDef) : Owner = {
        println("Package defn: "+p.name+" ["+this+"]")
        
        new Builder {
          val parent = self
          override def isPackage = true
        }
      }
    }
    
    trait ClassOwner extends Owner { self =>
      override def addClass(c : ClassDef) : Owner = {
        println("Class defn: "+c.name+" ["+this+"]")
        println("Parents: "+c.impl.parents)
        
        val name = c.symbol.simpleName.toString
        
        val parentTree = c.impl.parents.head
        val superclassType = parentTree.tpe
        val (superclassName, primaryType, interfaceTrees) =
          if (superclassType == null)
            (null, null, c.impl.parents)
          else if (superclassType.typeSymbol.isTrait)
            (null, superclassType.typeSymbol, c.impl.parents)
          else {
            val interfaceTrees0 = c.impl.parents.drop(1) 
            val superclassName0 = superclassType.typeSymbol.fullNameString
            if (superclassName0 == "java.lang.Object") {
              if (interfaceTrees0.isEmpty)
                ("java.lang.Object".toCharArray, null, interfaceTrees0)
              else
                (null, interfaceTrees0.head.tpe.typeSymbol, interfaceTrees0)
            }
            else
              (superclassName0.toCharArray, superclassType.typeSymbol, interfaceTrees0)   
          }

        
        val mask = ~(if (c.symbol.isAnonymousClass) ClassFileConstants.AccPublic else 0)
        
        val interfaceTypes = interfaceTrees.map(t => (t, t.tpe))
        val interfaceNames = interfaceTypes.map({ case (tree, tpe) => (if (tpe ne null) tpe.typeSymbol.fullNameString else "null-"+tree).toCharArray })
        
        indexer.addClassDeclaration(
          mapModifiers(c.mods) & mask,
          c.symbol.enclosingPackage.fullNameString.toCharArray,
          name.toCharArray,
          enclosingTypeNames(c.symbol).map(_.toArray).toArray,
          superclassName,
          interfaceNames.toArray,
          new Array[Array[Char]](0),
          true
        )
        
        new Builder {
          val parent = self
          
          override def isTemplate = true
          override def template = this
        }
      }
    }
    
    trait ModuleOwner extends Owner { self =>
      override def addModule(m : ModuleDef) : Owner = {
        println("Module defn: "+m.name+" ["+this+"]")
        
        val name = m.symbol.simpleName.toString

        val parentTree = m.impl.parents.head
        val superclassType = parentTree.tpe
        val superclassName = (if (superclassType ne null) superclassType.typeSymbol.fullNameString else "null-"+parentTree).toCharArray
        
        val interfaceTrees = m.impl.parents.drop(1)
        val interfaceTypes = interfaceTrees.map(t => (t, t.tpe))
        val interfaceNames = interfaceTypes.map({ case (tree, tpe) => (if (tpe ne null) tpe.typeSymbol.fullNameString else "null-"+tree).toCharArray })
        
        indexer.addClassDeclaration(
          mapModifiers(m.mods),
          m.symbol.enclosingPackage.fullNameString.toCharArray,
          (name+"$").toCharArray,
          enclosingTypeNames(m.symbol).map(_.toArray).toArray,
          superclassName,
          interfaceNames.toArray,
          new Array[Array[Char]](0),
          true
        )
        
        indexer.addFieldDeclaration(
          (m.symbol.fullNameString+"$").toCharArray,
          "MODULE$".toCharArray
        )

        indexer.addClassDeclaration(
          mapModifiers(m.mods),
          m.symbol.enclosingPackage.fullNameString.toCharArray,
          name.toString.toCharArray,
          new Array[Array[Char]](0),
          superclassName,
          interfaceNames.toArray,
          new Array[Array[Char]](0),
          true
        )

        new Builder {
          val parent = self
          
          override def isTemplate = true
          override def template = this
        }
      }
    }
    
    trait ValOwner extends Owner { self =>
      override def addVal(v : ValDef) : Owner = {
        println("Val defn: >"+nme.getterName(v.name)+"< ["+this+"]")
        
        indexer.addMethodDeclaration(
          nme.getterName(v.name).toString.toCharArray,
          new Array[Array[Char]](0),
          mapType(v.tpt).toArray,
          new Array[Array[Char]](0)
        )
        
        if(v.mods.hasFlag(Flags.MUTABLE))
          indexer.addMethodDeclaration(
            nme.getterToSetter(nme.getterName(v.name)).toString.toCharArray,
            new Array[Array[Char]](0),
            mapType(v.tpt).toArray,
            new Array[Array[Char]](0)
          )
        
        new Builder {
          val parent = self
          
          override def addDef(d : DefDef) = this
          override def addVal(v: ValDef) = this
          override def addType(t : TypeDef) = this
        }
      }
    }
    
    trait DefOwner extends Owner { self =>
      override def addDef(d : DefDef) : Owner = {
        println("Def defn: "+d.name+" ["+this+"]")
        val isCtor0 = d.name.toString == "<init>"
        val nm =
          if(isCtor0)
            currentOwner.simpleName
          else
            d.name
        
        val fps = for(vps <- d.vparamss; vp <- vps) yield vp
        
        val paramTypes = fps.map(v => mapType(v.tpt))
        indexer.addMethodDeclaration(
          nm.toString.toCharArray,
          paramTypes.map(_.toCharArray).toArray,
          mapType(d.tpt).toArray,
          new Array[Array[Char]](0)
        )
        
        new Builder {
          val parent = self
  
          override def isCtor = isCtor0
          override def addVal(v : ValDef) = this
          override def addType(t : TypeDef) = this
        }
      }
    }
    
    class CompilationUnitBuilder extends PackageOwner with ClassOwner with ModuleOwner {
      val parent = null
      override def compilationUnitBuilder = this 
    }
    
    abstract class Builder extends PackageOwner with ClassOwner with ModuleOwner with ValOwner with DefOwner
    
    override def traverse(tree: Tree): Unit = tree match {
      case pd : PackageDef => atBuilder(currentBuilder.addPackage(pd)) { super.traverse(tree) }
      case cd : ClassDef => {
        val cb = currentBuilder.addClass(cd)
        atOwner(tree.symbol) {
          atBuilder(cb) {
            //traverseTrees(cd.mods.annotations)
            //traverseTrees(cd.tparams)
            traverse(cd.impl)
          }
        }
      }
      case md : ModuleDef => atBuilder(currentBuilder.addModule(md)) { super.traverse(tree) }
      case vd : ValDef => {
        val vb = currentBuilder.addVal(vd)
        atOwner(tree.symbol) {
          atBuilder(vb) {
            //traverseTrees(vd.mods.annotations)
            //traverse(vd.tpt)
            traverse(vd.rhs)
          }
        }
      }
      case td : TypeDef => {
        val tb = currentBuilder.addType(td)
        atOwner(tree.symbol) {
          atBuilder(tb) {
            //traverseTrees(td.mods.annotations);
            //traverseTrees(td.tparams);
            traverse(td.rhs)
          }
        }
      }
      case dd : DefDef => {
        if(dd.name != nme.MIXIN_CONSTRUCTOR) {
          val db = currentBuilder.addDef(dd)
          atOwner(tree.symbol) {
            // traverseTrees(dd.mods.annotations)
            // traverseTrees(dd.tparams)
            //if(db.isCtor)
            //  atBuilder(currentBuilder) { traverseTreess(dd.vparamss) }
            //else
            //  atBuilder(db) { traverseTreess(dd.vparamss) }
            atBuilder(db) {
              traverse(dd.tpt)
              traverse(dd.rhs)
            }
          }
        }
      }
      case Template(parents, self, body) => {
        println("Template: "+parents)
        //traverseTrees(parents)
        //if (!self.isEmpty) traverse(self)
        traverseStats(body, tree.symbol)
      }
      case Function(vparams, body) => {
        println("Anonymous function: "+tree.symbol.simpleName)
      }
      case tt : TypeTree => {
        //println("Type tree: "+tt)
        //atBuilder(currentBuilder.addTypeTree(tt)) { super.traverse(tree) }            
        super.traverse(tree)            
      }
      case u =>
        //println("Unknown type: "+u.getClass.getSimpleName+" "+u)
        super.traverse(tree)
    }

    def atBuilder(builder: Owner)(traverse: => Unit) {
      val prevBuilder = currentBuilder
      currentBuilder = builder
      traverse
      currentBuilder = prevBuilder
    }
  }
}
