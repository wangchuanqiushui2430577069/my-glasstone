import numpy as np

# glasstone 模块中复用的基础工具
class ValueOutsideGraphError(Exception):
    """表示请求的输入值超出了原始资料图表的范围。"""
    def __init__(self, value):
        """记录超出图表范围的原始输入值。"""
        self.value = value
    def __str__(self):
        """返回异常值的字符串表示。"""
        return repr(self.value)

# 历史核武器效应模型使用了大量非 SI 单位，而且不同模型还常常混用多种单位。
# 提供这个单位换算函数，是为了让 glasstone 各部分都能以统一的 SI 输入工作。

class UnknownUnitError(Exception):
    """表示请求转换的单位未知。"""
    def __init__(self, value):
        """记录无法识别的单位对。"""
        self.value = value
    def __str__(self):
        """返回未知单位信息的字符串表示。"""
        return repr(self.value)
    
def convert_units(v, unitsfrom, unitsto):
    """在 glasstone 支持的历史单位体系之间做数值换算。"""
    distance_units = {
        'm': 1.0,
        'meters': 1.0,
        'km': 1000.0,
        'ft': 0.3048,
        'yards': 0.9144,
        'kilofeet': 304.8,
        'mi': 1609.34,
    }
    if unitsfrom == unitsto:
        return v
    # 当量
    elif unitsfrom == 'kT' and unitsto== 'MT':
        return v / 1000.0
    elif unitsfrom == 'MT' and unitsto== 'kT':
        return v * 1000.0
    # 距离
    elif unitsfrom in distance_units and unitsto in distance_units:
        return v * distance_units[unitsfrom] / distance_units[unitsto]
    # 压力
    elif unitsfrom == 'psi' and unitsto== 'kg/cm^2':
        return v * 0.070307
    elif unitsfrom == 'kg/cm^2' and unitsto== 'psi':
        return v / 0.070307
    elif unitsfrom == 'MPa' and unitsto== 'psi':
        return v * 145.037738
    elif unitsfrom == 'psi' and unitsto== 'MPa':
        return v / 145.037738
    elif unitsfrom == 'kg/cm^2' and unitsto== 'MPa':
        return convert_units(convert_units(v, 'kg/cm^2', 'psi'), 'psi', 'MPa')
    elif unitsfrom == 'MPa' and unitsto== 'kg/cm^2':
        return convert_units(convert_units(v, 'psi', 'kg/cm^2'), 'MPa', 'psi')
    elif unitsfrom =='Pa':
        return convert_units(v, 'MPa', unitsto) / 1e6
    elif unitsto == 'Pa':
        return convert_units(v, unitsfrom, 'MPa') * 1e6
    # 速度
    elif unitsfrom == 'm/s' and unitsto== 'mph':
        return v * 2.23694
    elif unitsfrom == 'mph' and unitsto== 'm/s':
        return v / 2.23694
    elif unitsfrom == 'm/s' and unitsto== 'km/h':
        return v * 3.6
    elif unitsfrom == 'km/h' and unitsto== 'm/s':
        return v / 3.6
    elif unitsfrom == 'mph' and unitsto== 'km/h':
        return v * 1.60934
    elif unitsfrom == 'km/h' and unitsto== 'mph':
        return v / 1.60934
    # 风切变
    elif unitsfrom == 'm/s-km' and unitsto == 'mph/kilofoot':
        return v * 0.13625756613945836
    elif unitsfrom == 'mph/kilofoot' and unitsto == 'm/s-km':
        return v / 0.13625756613945836
    # 剂量
    # 严格来说这里并不完全精确，因为 Roentgen 通常表示照射量而不是剂量。
    # 不过 WSEG-10 使用了一种特殊单位 Equivalent Residual Dose，可直接换算为 Sv：
    elif unitsfrom == 'Roentgen' and unitsto == 'Sv':
        return v / 100.0
    elif unitsfrom == 'Sv' and unitsto == 'Roentgen':
        return v * 100.0
    else:
        raise UnknownUnitError((unitsfrom, unitsto))

def dict_reverse(d):
    """把字典中的每个序列值按相反顺序复制出来。"""
    new_dict = {}
    for k in d:
        new_dict[k] = d[k][::-1]
    return new_dict
