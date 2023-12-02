interface Props {
  dismissable: boolean;
  type: string;
  onClose: () => void;
}

const Alert = ({ dismissable, type, onClose }: Props) => {
  return (
    <div
      className={`alert alert-${type} ${dismissable && 'alert-dismissible'}`}
    >
      Alert
      {dismissable && <button className='btn-close' onClick={onClose} />}
    </div>
  );
};

export default Alert;
