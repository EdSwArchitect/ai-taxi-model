<?xml version="1.0" encoding="UTF-8"?>
<StyledLayerDescriptor version="1.0.0" 
    xsi:schemaLocation="http://www.opengis.net/sld StyledLayerDescriptor.xsd" 
    xmlns="http://www.opengis.net/sld" 
    xmlns:ogc="http://www.opengis.net/ogc" 
    xmlns:xlink="http://www.w3.org/1999/xlink" 
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <NamedLayer>
    <Name>green_taxi_style</Name>
    <UserStyle>
      <Title>Green Taxi Trip Style</Title>
      <Abstract>Style for Green Taxi (SHL) trip data matching the Parquet schema</Abstract>
      
      <!-- Feature Type Style for points/polygons -->
      <FeatureTypeStyle>
        
        <!-- Rule 1: High fare trips (total_amount > 50) -->
        <Rule>
          <Name>High Fare Trips</Name>
          <Title>High Fare Trips (&gt; $50)</Title>
          <ogc:Filter>
            <ogc:And>
              <ogc:PropertyIsGreaterThan>
                <ogc:PropertyName>total_amount</ogc:PropertyName>
                <ogc:Literal>50</ogc:Literal>
              </ogc:PropertyIsGreaterThan>
              <ogc:PropertyIsNotNull>
                <ogc:PropertyName>total_amount</ogc:PropertyName>
              </ogc:PropertyIsNotNull>
            </ogc:And>
          </ogc:Filter>
          <PointSymbolizer>
            <Graphic>
              <Mark>
                <WellKnownName>circle</WellKnownName>
                <Fill>
                  <CssParameter name="fill">#FF0000</CssParameter>
                  <CssParameter name="fill-opacity">0.7</CssParameter>
                </Fill>
                <Stroke>
                  <CssParameter name="stroke">#8B0000</CssParameter>
                  <CssParameter name="stroke-width">2</CssParameter>
                </Stroke>
              </Mark>
              <Size>12</Size>
            </Graphic>
          </PointSymbolizer>
        </Rule>
        
        <!-- Rule 2: Medium fare trips (20 < total_amount <= 50) -->
        <Rule>
          <Name>Medium Fare Trips</Name>
          <Title>Medium Fare Trips ($20-$50)</Title>
          <ogc:Filter>
            <ogc:And>
              <ogc:PropertyIsGreaterThan>
                <ogc:PropertyName>total_amount</ogc:PropertyName>
                <ogc:Literal>20</ogc:Literal>
              </ogc:PropertyIsGreaterThan>
              <ogc:PropertyIsLessThanOrEqualTo>
                <ogc:PropertyName>total_amount</ogc:PropertyName>
                <ogc:Literal>50</ogc:Literal>
              </ogc:PropertyIsLessThanOrEqualTo>
            </ogc:And>
          </ogc:Filter>
          <PointSymbolizer>
            <Graphic>
              <Mark>
                <WellKnownName>circle</WellKnownName>
                <Fill>
                  <CssParameter name="fill">#FFA500</CssParameter>
                  <CssParameter name="fill-opacity">0.7</CssParameter>
                </Fill>
                <Stroke>
                  <CssParameter name="stroke">#FF8C00</CssParameter>
                  <CssParameter name="stroke-width">1.5</CssParameter>
                </Stroke>
              </Mark>
              <Size>10</Size>
            </Graphic>
          </PointSymbolizer>
        </Rule>
        
        <!-- Rule 3: Low fare trips (total_amount <= 20) -->
        <Rule>
          <Name>Low Fare Trips</Name>
          <Title>Low Fare Trips (&lt;= $20)</Title>
          <ogc:Filter>
            <ogc:And>
              <ogc:PropertyIsLessThanOrEqualTo>
                <ogc:PropertyName>total_amount</ogc:PropertyName>
                <ogc:Literal>20</ogc:Literal>
              </ogc:PropertyIsLessThanOrEqualTo>
              <ogc:PropertyIsNotNull>
                <ogc:PropertyName>total_amount</ogc:PropertyName>
              </ogc:PropertyIsNotNull>
            </ogc:And>
          </ogc:Filter>
          <PointSymbolizer>
            <Graphic>
              <Mark>
                <WellKnownName>circle</WellKnownName>
                <Fill>
                  <CssParameter name="fill">#00FF00</CssParameter>
                  <CssParameter name="fill-opacity">0.7</CssParameter>
                </Fill>
                <Stroke>
                  <CssParameter name="stroke">#006400</CssParameter>
                  <CssParameter name="stroke-width">1</CssParameter>
                </Stroke>
              </Mark>
              <Size>8</Size>
            </Graphic>
          </PointSymbolizer>
        </Rule>
        
        <!-- Rule 4: Default style for records without total_amount -->
        <Rule>
          <Name>Default</Name>
          <Title>Other Trips</Title>
          <ElseFilter/>
          <PointSymbolizer>
            <Graphic>
              <Mark>
                <WellKnownName>circle</WellKnownName>
                <Fill>
                  <CssParameter name="fill">#808080</CssParameter>
                  <CssParameter name="fill-opacity">0.5</CssParameter>
                </Fill>
                <Stroke>
                  <CssParameter name="stroke">#000000</CssParameter>
                  <CssParameter name="stroke-width">1</CssParameter>
                </Stroke>
              </Mark>
              <Size>6</Size>
            </Graphic>
          </PointSymbolizer>
        </Rule>
        
        <!-- Label based on trip distance and fare -->
        <Rule>
          <Name>Labels</Name>
          <TextSymbolizer>
            <Label>
              <ogc:PropertyName>total_amount</ogc:PropertyName>
            </Label>
            <Font>
              <CssParameter name="font-family">Arial</CssParameter>
              <CssParameter name="font-size">10</CssParameter>
              <CssParameter name="font-style">normal</CssParameter>
              <CssParameter name="font-weight">bold</CssParameter>
            </Font>
            <LabelPlacement>
              <PointPlacement>
                <AnchorPoint>
                  <AnchorPointX>0.5</AnchorPointX>
                  <AnchorPointY>0.5</AnchorPointY>
                </AnchorPoint>
                <Displacement>
                  <DisplacementX>0</DisplacementX>
                  <DisplacementY>15</DisplacementY>
                </Displacement>
              </PointPlacement>
            </LabelPlacement>
            <Fill>
              <CssParameter name="fill">#000000</CssParameter>
            </Fill>
            <VendorOption name="autoWrap">60</VendorOption>
            <VendorOption name="maxDisplacement">50</VendorOption>
          </TextSymbolizer>
        </Rule>
        
      </FeatureTypeStyle>
      
      <!-- Feature Type Style for line features (if trip routes are available) -->
      <FeatureTypeStyle>
        <Rule>
          <Name>Trip Routes</Name>
          <Title>Trip Routes</Title>
          <LineSymbolizer>
            <Stroke>
              <CssParameter name="stroke">#00AA00</CssParameter>
              <CssParameter name="stroke-width">
                <ogc:Function name="Categorize">
                  <ogc:PropertyName>trip_distance</ogc:PropertyName>
                  <ogc:Literal>1</ogc:Literal>
                  <ogc:Literal>0</ogc:Literal>
                  <ogc:Literal>1</ogc:Literal>
                  <ogc:Literal>2</ogc:Literal>
                  <ogc:Literal>5</ogc:Literal>
                  <ogc:Literal>3</ogc:Literal>
                  <ogc:Literal>10</ogc:Literal>
                  <ogc:Literal>4</ogc:Literal>
                </ogc:Function>
              </CssParameter>
              <CssParameter name="stroke-opacity">0.6</CssParameter>
              <CssParameter name="stroke-dasharray">5 2</CssParameter>
            </Stroke>
          </LineSymbolizer>
        </Rule>
      </FeatureTypeStyle>
      
    </UserStyle>
  </NamedLayer>
</StyledLayerDescriptor>

