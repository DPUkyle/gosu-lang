/*
 * Copyright 2014 Guidewire Software, Inc.
 */

package gw.internal.gosu.parser.expressions;

import gw.config.CommonServices;
import gw.internal.gosu.parser.BeanAccess;
import gw.internal.gosu.parser.ParseTree;
import gw.internal.gosu.parser.ParserBase;
import gw.lang.IDimension;
import gw.lang.parser.ICoercionManager;
import gw.lang.parser.expressions.IAdditiveExpression;
import gw.lang.reflect.IMethodInfo;
import gw.lang.reflect.IPlaceholder;
import gw.lang.reflect.IType;
import gw.lang.reflect.ReflectUtil;
import gw.lang.reflect.TypeSystem;
import gw.lang.reflect.java.JavaTypes;
import gw.util.Rational;

import java.math.BigDecimal;


/**
 * Represents an additive expression in the Gosu grammar:
 * <pre>
 * <i>additive-expression</i>
 *   &lt;multiplicative-expression&gt;
 *   &lt;additive-expression&gt; <b>+</b> &lt;multiplicative-expression&gt;
 *   &lt;additive-expression&gt; <b>-</b> &lt;multiplicative-expression&gt;
 * </pre>
 * <p/>
 *
 * @see gw.lang.parser.IGosuParser
 */
public final class AdditiveExpression extends ArithmeticExpression implements IAdditiveExpression
{
  /**
   * Returns whether or not the operation is addition or substraction.
   *
   * @return True if operation is addition.
   */
  public boolean isAdditive()
  {
    return getOperator() != null && getOperator().endsWith( "+" );
  }

  // Tests if this expr is part of an assignment such as: size += 5, where this expr only contains the 5 as a child
  public boolean isAssignment()
  {
    ParseTree loc = getLocation();
    return loc != null && loc.getChildCount() < 2;
  }

  /**
   * Evaluates this additive expression.  Either performs numeric addition/subtraction or
   * String concatination.
   */
  public Object evaluate()
  {
    if( !isCompileTimeConstant() )
    {
      return super.evaluate();
    }

    return evaluate( getType(), getLHS().evaluate(), getRHS().evaluate(), getLHS().getType(), getRHS().getType(), isAdditive(), false, !getType().equals( JavaTypes.STRING() ) );
  }

  // Potentially called from generated bytecode

  public static Object evaluate( IType type, Object lhsValue, Object rhsValue, IType lhsType, IType rhsType, boolean bAdditive, boolean bNullSafe, boolean bNumericType, Object ctx, int startLhs, int endLhs, int startRhs, int endRhs )
  {
    return evaluate( type, lhsValue, rhsValue, lhsType, rhsType, bAdditive, bNullSafe, bNumericType );
  }

  public static Object evaluate( IType type, Object lhsValue, Object rhsValue, IType lhsType, IType rhsType, boolean bAdditive, boolean bNullSafe, boolean bNumericType )
  {
    boolean bDynamic = false;
    if( lhsType instanceof IPlaceholder && ((IPlaceholder)lhsType).isPlaceholder() )
    {
      bDynamic = true;
      lhsType = TypeSystem.getFromObject( lhsValue );
    }
    if( rhsType instanceof IPlaceholder && ((IPlaceholder)rhsType).isPlaceholder() )
    {
      bDynamic = true;
      rhsType = TypeSystem.getFromObject( rhsValue );
    }
    if( bDynamic )
    {
      ArithmeticExpression overrideMethod = new AdditiveExpression();
      type = ParserBase.resolveRuntimeType( overrideMethod, lhsType, bAdditive ? '+' : '-', rhsType );
      IMethodInfo mi = overrideMethod.getOverride();
      if( mi != null )
      {
        return mi.getCallHandler().handleCall( lhsValue, ReflectUtil.coerceArgsIfNecessary( mi.getParameters(), rhsValue ) );
      }

      bNumericType = BeanAccess.isNumericType( type );
    }

    ICoercionManager cm = CommonServices.getCoercionManager();
    if( bNumericType )
    {
      // Only evaluate as null if this is a numeric expression.
      // String concatenation should behave like Java regarding null values.
      if( lhsValue == null )
      {
        if( bNullSafe )
        {
          return null;
        }
        throw new NullPointerException("left-hand operand was null");
      }
      if( rhsValue == null )
      {
        if( bNullSafe )
        {
          return null;
        }
        throw new NullPointerException("right-hand operand was null");
      }

      IDimension customNumberBase = null;
      if( JavaTypes.IDIMENSION().isAssignableFrom( type ) ) {
        DimensionOperandResolver dimOperandResolver =
          DimensionOperandResolver.resolve( type, lhsType, lhsValue, rhsType, rhsValue );
        type = dimOperandResolver.getRawNumberType();
        lhsValue = dimOperandResolver.getLhsValue();
        rhsValue = dimOperandResolver.getRhsValue();
        customNumberBase = dimOperandResolver.getBase();
      }

      // Add/Subtract values as numbers
      Object retValue;
      if( bAdditive )
      {
        if( type == JavaTypes.RATIONAL() )
        {
          retValue = cm.makeRationalFrom( lhsValue ).add( cm.makeRationalFrom( rhsValue ) );
        }
        else if( type == JavaTypes.BIG_DECIMAL() )
        {
          retValue = cm.makeBigDecimalFrom( lhsValue ).add( cm.makeBigDecimalFrom( rhsValue ) );
        }
        else if( type == JavaTypes.BIG_INTEGER() )
        {
          retValue = cm.makeBigIntegerFrom( lhsValue ).add( cm.makeBigIntegerFrom( rhsValue ) );
        }
        else if( type == JavaTypes.INTEGER() || type == JavaTypes.pINT() )
        {
          retValue = cm.makeIntegerFrom( lhsValue ) + cm.makeIntegerFrom( rhsValue );
        }
        else if( type == JavaTypes.LONG() || type == JavaTypes.pLONG() )
        {
          retValue = makeLong( cm.makeLongFrom( lhsValue ) + cm.makeLongFrom( rhsValue ) );
        }
        else if( type == JavaTypes.DOUBLE() || type == JavaTypes.pDOUBLE() )
        {
          retValue = makeDoubleValue( makeDoubleValue( lhsValue ) + makeDoubleValue( rhsValue ) );
        }
        else if( type == JavaTypes.FLOAT() || type == JavaTypes.pFLOAT() )
        {
          retValue = makeFloatValue( makeFloatValue( lhsValue ) + makeFloatValue( rhsValue ) );
        }
        else if( type == JavaTypes.SHORT() || type == JavaTypes.pSHORT() )
        {
          retValue = Integer.valueOf( cm.makeIntegerFrom( lhsValue ) + cm.makeIntegerFrom( rhsValue ) ).shortValue();
        }
        else if( type == JavaTypes.BYTE() || type == JavaTypes.pBYTE() )
        {
          retValue = (byte)(cm.makeIntegerFrom( lhsValue ) + cm.makeIntegerFrom( rhsValue ));
        }
        else
        {
          throw new UnsupportedNumberTypeException(type);
        }
      }
      else
      {
        if( type == JavaTypes.RATIONAL() )
        {
          retValue = cm.makeRationalFrom( lhsValue ).subtract( cm.makeRationalFrom( rhsValue ) );
        }
        else if( type == JavaTypes.BIG_DECIMAL() )
        {
          retValue = cm.makeBigDecimalFrom( lhsValue ).subtract( cm.makeBigDecimalFrom( rhsValue ) );
        }
        else if( type == JavaTypes.BIG_INTEGER() )
        {
          retValue = cm.makeBigIntegerFrom( lhsValue ).subtract( cm.makeBigIntegerFrom( rhsValue ) );
        }
        else if( type == JavaTypes.INTEGER() || type == JavaTypes.pINT() )
        {
          retValue = cm.makeIntegerFrom( lhsValue ) - cm.makeIntegerFrom( rhsValue );
        }
        else if( type == JavaTypes.LONG() || type == JavaTypes.pLONG() )
        {
          retValue = makeLong( cm.makeLongFrom( lhsValue ) - cm.makeLongFrom( rhsValue ) );
        }
        else if( type == JavaTypes.DOUBLE() || type == JavaTypes.pDOUBLE() )
        {
          retValue = makeDoubleValue( makeDoubleValue( lhsValue ) - makeDoubleValue( rhsValue ) );
        }
        else if( type == JavaTypes.FLOAT() || type == JavaTypes.pFLOAT() )
        {
          retValue = makeFloatValue( makeFloatValue( lhsValue ) - makeFloatValue( rhsValue ) );
        }
        else if( type == JavaTypes.SHORT() || type == JavaTypes.pSHORT() )
        {
          retValue = Integer.valueOf( cm.makeIntegerFrom( lhsValue ) - cm.makeIntegerFrom( rhsValue ) ).shortValue();
        }
        else if( type == JavaTypes.BYTE() || type == JavaTypes.pBYTE() )
        {
          retValue = (byte)(cm.makeIntegerFrom( lhsValue ) - cm.makeIntegerFrom( rhsValue ));
        }
        else
        {
          throw new UnsupportedNumberTypeException( type );
        }
      }
      if( retValue != null )
      {
        if( customNumberBase != null )
        {
          //noinspection unchecked
          retValue = customNumberBase.fromNumber( (Number)retValue );
        }
      }
      return retValue;
    }
    else
    {
      // Concatenate values as strings

      return cm.makeStringFrom( lhsValue ) + cm.makeStringFrom( rhsValue );
    }
  }
}
