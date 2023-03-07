using CommunityToolkit.Mvvm.ComponentModel;
using LiveChartsCore.Defaults;
using LiveChartsCore.SkiaSharpView;
using LiveChartsCore;
using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using System;
using System.Collections.Generic;
using System.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using LiveChartsCore.SkiaSharpView.Painting;
using SkiaSharp;

namespace ViewModelsSamples.Lines.AutoUpdate;

[ObservableObject]
public partial class ViewModel
{
    private readonly Random _random = new();
    private readonly ObservableCollection<ObservableValue> _observableValues;

    public ViewModel()
    {
        // Use ObservableCollections to let the chart listen for changes (or any INotifyCollectionChanged). 
        _observableValues = new ObservableCollection<ObservableValue>
            {
                // Use the ObservableValue or ObservablePoint types to let the chart listen for property changes 
                // or use any INotifyPropertyChanged implementation 
                new ObservableValue(0),
            };

        Series = new ObservableCollection<ISeries>
            {
                new LineSeries<ObservableValue>
                {
                    Values = _observableValues,
                    Stroke = new SolidColorPaint(SKColors.WhiteSmoke) { StrokeThickness = 2 },
                    GeometryStroke = null,
                    GeometrySize = 0,
                    Fill = null
                }
            };

        // in the following sample notice that the type int does not implement INotifyPropertyChanged
        // and our Series.Values property is of type List<T>
        // List<T> does not implement INotifyCollectionChanged
        // this means the following series is not listening for changes.
        // Series.Add(new ColumnSeries<int> { Values = new List<int> { 2, 4, 6, 1, 7, -2 } }); 
    }
    public ObservableCollection<ISeries> Series { get; set; }
    [RelayCommand]
    public void AddItem(float item)
    {
        _observableValues.Add(new(item));
    }
    [RelayCommand]
    public void RemoveItem()
    {
        if (_observableValues.Count == 0) return;
        _observableValues.RemoveAt(0);
    }

    [RelayCommand]
    public void RemoveSeries()
    {

        _observableValues.Clear();
    }
    public int length()
    {
        return _observableValues.Count(); 
    }

    public void scroll(float item)
    {
        _observableValues.RemoveAt(0);
        _observableValues.Add(new(item));

    }
}

